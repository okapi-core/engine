/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.ch;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.query.GenericRecord;
import gg.jte.TemplateOutput;
import gg.jte.output.StringOutput;
import java.util.*;
import org.okapi.ch.ChTemplateFiles;
import org.okapi.metrics.ch.ChConstants;
import org.okapi.metrics.ch.template.ChGetHistoQueryTemplate;
import org.okapi.metrics.ch.template.ChGetSumQueryTemplate;
import org.okapi.metrics.ch.template.ChMetricTemplateEngine;
import org.okapi.metrics.pojos.results.GaugeScan;
import org.okapi.metrics.pojos.results.Scan;
import org.okapi.metrics.pojos.results.SumScan;
import org.okapi.promql.eval.HistogramSeries;
import org.okapi.promql.eval.HistogramSeries.NativeHistogramSample;
import org.okapi.promql.eval.ts.RESOLUTION;
import org.okapi.promql.eval.ts.TsClient;

public class ChPromQlTsClient implements TsClient {
  private static final String GET_GAUGE_RAW_SAMPLES_EXACT_MATCH =
      "get_gauge_raw_samples_exact_match.jte";
  private static final String GET_HISTO_SAMPLES_EXACT_MATCH =
      "get_histo_samples_exact_match.jte";
  private static final String GET_EXPONENTIAL_HISTO_SAMPLES_EXACT_MATCH =
      "get_exponential_histo_samples_exact_match.jte";
  private static final String GET_SUM_SAMPLES_EXACT_MATCH =
      "get_sum_samples_exact_match.jte";
  private static final String GET_METRIC_EVENT_TYPE_EXACT_MATCH =
      "get_metric_event_type_exact_match.jte";
  private static final Set<String> INTERNAL_LABELS = Set.of("__name__", "__type__", "__unit__");

  private final Client client;
  private final ChMetricTemplateEngine templateEngine;

  public ChPromQlTsClient(Client client, ChMetricTemplateEngine templateEngine) {
    this.client = client;
    this.templateEngine = templateEngine;
  }

  @Override
  public Scan get(String name, Map<String, String> tags, RESOLUTION res, long startMs, long endMs) {
    var labels = splitLabels(tags);
    // todo: cache this information, type information does not change that often.
    MetricEventType type = resolveMetricType(name, labels.tags(), labels.unit(), startMs, endMs);

    return switch (type) {
      case HISTO -> getHistogramSeries(name, labels.tags(), labels.unit(), startMs, endMs);
      case SUM -> getSumSeries(name, labels.tags(), labels.unit(), startMs, endMs);
      case GAUGE -> getGaugeSeries(name, labels.tags(), labels.unit(), startMs, endMs);
    };
  }

  private MetricEventType resolveMetricType(
      String metric, Map<String, String> tags, String unit, long startMs, long endMs) {
    TemplateOutput output = new StringOutput();
    templateEngine.render(
        GET_METRIC_EVENT_TYPE_EXACT_MATCH,
        ChMetricEventTypeQueryTemplate.builder()
            .table(ChConstants.TBL_METRIC_EVENTS_META)
            .metric(ChSqlEscaper.escapeLiteral(metric))
            .startMs(startMs)
            .endMs(endMs)
            .tags(ChSqlEscaper.escapeTags(tags))
            .unit(ChSqlEscaper.escapeLiteral(unit))
            .build(),
        output);
    var query = output.toString();
    List<GenericRecord> records = client.queryAll(query);
    return uniqueMetricEventType(records.stream().map(r -> r.getString("event_type")).toList())
        .orElse(MetricEventType.GAUGE);
  }

  private GaugeScan getGaugeSeries(
      String metric, Map<String, String> tags, String unit, long startMs, long endMs) {
    TemplateOutput output = new StringOutput();
    templateEngine.render(
        GET_GAUGE_RAW_SAMPLES_EXACT_MATCH,
        ChGetGaugeRawQueryTemplate.builder()
            .table(ChConstants.TBL_GAUGES)
            .metric(ChSqlEscaper.escapeLiteral(metric))
            .startMs(startMs)
            .endMs(endMs)
            .tags(ChSqlEscaper.escapeTags(tags))
            .unit(ChSqlEscaper.escapeLiteral(unit))
            .build(),
        output);
    var query = output.toString();
    List<GenericRecord> records = client.queryAll(query);
    var times = new ArrayList<Long>(records.size());
    var values = new ArrayList<Float>(records.size());
    for (var record : records) {
      times.add(record.getLong("ts_ms"));
      values.add((float) record.getDouble("value"));
    }
    return GaugeScan.builder().universalPath(metric).timestamps(times).values(values).build();
  }

  private Scan getSumSeries(
      String metric, Map<String, String> tags, String unit, long startMs, long endMs) {
    var delta = scanSumSamples(metric, tags, unit, startMs, endMs, "DELTA");
    if (!delta.isEmpty()) {
      return toSumScan(metric, delta, false);
    }
    var cumulative = scanSumSamples(metric, tags, unit, startMs, endMs, "CUMULATIVE");
    return toSumScan(metric, cumulative, true);
  }

  private List<SumPoint> scanSumSamples(
      String metric, Map<String, String> tags, String unit, long startMs, long endMs, String type) {
    TemplateOutput output = new StringOutput();
    templateEngine.render(
        GET_SUM_SAMPLES_EXACT_MATCH,
        ChGetSumQueryTemplate.builder()
            .table(ChConstants.TBL_SUM)
            .metric(ChSqlEscaper.escapeLiteral(metric))
            .tags(ChSqlEscaper.escapeTags(tags))
            .unit(ChSqlEscaper.escapeLiteral(unit))
            .sumsType(type)
            .ts(startMs)
            .te(endMs)
            .build(),
        output);
    var query = output.toString();
    List<GenericRecord> records = client.queryAll(query);
    var points = new ArrayList<SumPoint>(records.size());
    for (var record : records) {
      points.add(
          new SumPoint(
              record.getLong("ts_start_ms"),
              record.getLong("ts_end_ms"),
              record.getDouble("value")));
    }
    points.sort(Comparator.comparingLong(SumPoint::endMs));
    return points;
  }

  static SumScan toSumScan(String metric, List<SumPoint> points, boolean cumulative) {
    var ts = new ArrayList<Long>(points.size());
    var counts = new ArrayList<Double>(points.size());
    Double prev = null;
    for (var p : points) {
      ts.add(p.endMs());
      double val = p.value();
      double delta = val;
      if (cumulative && prev != null) {
        delta = val - prev;
        if (delta < 0) delta = val; // counter reset
      }
      counts.add(delta);
      prev = val;
    }
    return SumScan.builder().universalPath(metric).ts(ts).counts(counts).windowSize(0).build();
  }

  private Scan getHistogramSeries(
      String metric, Map<String, String> tags, String unit, long startMs, long endMs) {
    var delta = scanHistoSamples(metric, tags, unit, startMs, endMs, "DELTA");
    var exponentialDelta =
        scanExponentialHistoSamples(metric, tags, unit, startMs, endMs, "DELTA");
    ensureSingleHistogramRepresentation(delta, exponentialDelta);
    if (!delta.isEmpty()) return new HistogramSeries(metric, delta);
    if (!exponentialDelta.isEmpty()) return new HistogramSeries(metric, exponentialDelta);
    var cumulative = scanHistoSamples(metric, tags, unit, startMs, endMs, "CUMULATIVE");
    var exponentialCumulative =
        scanExponentialHistoSamples(metric, tags, unit, startMs, endMs, "CUMULATIVE");
    ensureSingleHistogramRepresentation(cumulative, exponentialCumulative);
    if (!cumulative.isEmpty()) return new HistogramSeries(metric, toDeltaHistos(cumulative));
    if (!exponentialCumulative.isEmpty())
      return new HistogramSeries(metric, toDeltaHistos(exponentialCumulative));
    return new HistogramSeries(metric, List.of());
  }

  private List<NativeHistogramSample> scanHistoSamples(
      String metric, Map<String, String> tags, String unit, long startMs, long endMs, String type) {
    TemplateOutput output = new StringOutput();
    templateEngine.render(
        GET_HISTO_SAMPLES_EXACT_MATCH,
        ChGetHistoQueryTemplate.builder()
            .table(ChConstants.TBL_HISTOS)
            .metric(ChSqlEscaper.escapeLiteral(metric))
            .tags(ChSqlEscaper.escapeTags(tags))
            .unit(ChSqlEscaper.escapeLiteral(unit))
            .histoType(type)
            .ts(startMs)
            .te(endMs)
            .build(),
        output);
    var query = output.toString();
    List<GenericRecord> records = client.queryAll(query);
    var points = new ArrayList<NativeHistogramSample>(records.size());
    for (var record : records) {
      double[] buckets = readDoubleArray(record, "buckets");
      double[] counts = readDoubleArray(record, "counts");
      points.add(
          nativeCustomHistogram(
              record.getLong("ts_start_ms"),
              record.getLong("ts_end_ms"),
              buckets,
              counts,
              record.hasValue("sum") ? record.getDouble("sum") : Double.NaN,
              record.getLong("count")));
    }
    points.sort(Comparator.comparingLong(NativeHistogramSample::endMs));
    return points;
  }

  static NativeHistogramSample nativeCustomHistogram(
      long startMs, long endMs, double[] buckets, double[] counts, double sum, double count) {
    return new NativeHistogramSample(
        startMs,
        endMs,
        HistogramSeries.CUSTOM_BUCKET_SCHEMA,
        0d,
        0d,
        0,
        counts,
        0,
        new double[0],
        buckets,
        sum,
        count,
        "gauge");
  }

  private List<NativeHistogramSample> scanExponentialHistoSamples(
      String metric, Map<String, String> tags, String unit, long startMs, long endMs, String type) {
    TemplateOutput output = new StringOutput();
    templateEngine.render(
        GET_EXPONENTIAL_HISTO_SAMPLES_EXACT_MATCH,
        ChGetExponentialHistoQueryTemplate.builder()
            .table(ChConstants.TBL_EXPONENTIAL_HISTOS)
            .metric(ChSqlEscaper.escapeLiteral(metric))
            .tags(ChSqlEscaper.escapeTags(tags))
            .unit(ChSqlEscaper.escapeLiteral(unit))
            .histoType(type)
            .ts(startMs)
            .te(endMs)
            .build(),
        output);
    List<GenericRecord> records = client.queryAll(output.toString());
    var points = new ArrayList<NativeHistogramSample>(records.size());
    for (var record : records) {
      points.add(
          nativeExponentialHistogram(
              record.getLong("ts_start_ms"),
              record.getLong("ts_end_ms"),
              record.getInteger("scale"),
              record.getDouble("zero_threshold"),
              record.getLong("zero_count"),
              record.getInteger("positive_offset"),
              readDoubleArray(record, "positive_counts"),
              record.getInteger("negative_offset"),
              readDoubleArray(record, "negative_counts"),
              record.hasValue("sum") ? record.getDouble("sum") : Double.NaN,
              record.getLong("count")));
    }
    points.sort(Comparator.comparingLong(NativeHistogramSample::endMs));
    return points;
  }

  static NativeHistogramSample nativeExponentialHistogram(
      long startMs,
      long endMs,
      int scale,
      double zeroThreshold,
      double zeroCount,
      int positiveOffset,
      double[] positiveCounts,
      int negativeOffset,
      double[] negativeCounts,
      double sum,
      double count) {
    return new NativeHistogramSample(
        startMs,
        endMs,
        scale,
        zeroThreshold,
        zeroCount,
        positiveOffset,
        positiveCounts,
        negativeOffset,
        negativeCounts,
        new double[0],
        sum,
        count,
        "gauge");
  }

  static void ensureSingleHistogramRepresentation(
      List<NativeHistogramSample> explicit, List<NativeHistogramSample> exponential) {
    if (!explicit.isEmpty() && !exponential.isEmpty()) {
      throw new IllegalStateException(
          "series contains both explicit and exponential histogram representations");
    }
  }

  static List<NativeHistogramSample> toDeltaHistos(List<NativeHistogramSample> cumulative) {
    var out = new ArrayList<NativeHistogramSample>(cumulative.size());
    NativeHistogramSample prev = null;
    for (var p : cumulative) {
      out.add(
          prev == null || HistogramSeries.isReset(prev, p)
              ? p
              : (NativeHistogramSample) HistogramSeries.subtract(p, prev));
      prev = p;
    }
    return out;
  }

  private static double[] readDoubleArray(GenericRecord record, String key) {
    try {
      return record.getDoubleArray(key);
    } catch (Exception e) {
      var list = record.getList(key);
      if (list == null) {
        return new double[0];
      }
      double[] arr = new double[list.size()];
      for (int i = 0; i < list.size(); i++) {
        arr[i] = ((Number) list.get(i)).doubleValue();
      }
      return arr;
    }
  }

  static SeriesFetchLabels splitLabels(Map<String, String> labels) {
    var tags = new LinkedHashMap<String, String>();
    if (labels == null) {
      return new SeriesFetchLabels("", tags);
    }
    for (var entry : labels.entrySet()) {
      if (!INTERNAL_LABELS.contains(entry.getKey())) {
        tags.put(entry.getKey(), entry.getValue());
      }
    }
    return new SeriesFetchLabels(labels.getOrDefault("__unit__", ""), tags);
  }

  static Optional<MetricEventType> uniqueMetricEventType(List<String> eventTypes) {
    var types = eventTypes.stream().map(MetricEventType::valueOf).collect(java.util.stream.Collectors.toSet());
    if (types.size() > 1) {
      throw new IllegalStateException("series identity contains conflicting metric types: " + types);
    }
    return types.stream().findFirst();
  }

  enum MetricEventType {
    GAUGE,
    HISTO,
    SUM
  }

  record SeriesFetchLabels(String unit, Map<String, String> tags) {}

  record SumPoint(long startMs, long endMs, double value) {}
}

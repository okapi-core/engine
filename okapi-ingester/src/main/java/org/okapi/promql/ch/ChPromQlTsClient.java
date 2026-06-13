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
import org.okapi.promql.eval.HistogramSeries.ExplicitHistogramSample;
import org.okapi.promql.eval.HistogramSeries.Temporality;
import org.okapi.promql.eval.ts.RESOLUTION;
import org.okapi.promql.eval.ts.TsClient;

public class ChPromQlTsClient implements TsClient {
  private static final String GET_GAUGE_RAW_SAMPLES_EXACT_MATCH =
      "get_gauge_raw_samples_exact_match.jte";
  private static final String GET_HISTO_SAMPLES_EXACT_MATCH =
      "get_histo_samples_exact_match.jte";
  private static final String GET_SUM_SAMPLES_EXACT_MATCH =
      "get_sum_samples_exact_match.jte";
  private static final String GET_METRIC_EVENT_TYPE_EXACT_MATCH =
      "get_metric_event_type_exact_match.jte";

  private final Client client;
  private final ChMetricTemplateEngine templateEngine;

  public ChPromQlTsClient(Client client, ChMetricTemplateEngine templateEngine) {
    this.client = client;
    this.templateEngine = templateEngine;
  }

  @Override
  public Scan get(String name, Map<String, String> tags, RESOLUTION res, long startMs, long endMs) {
    Map<String, String> tagCopy = tags == null ? new LinkedHashMap<>() : new LinkedHashMap<>(tags);
    // todo: cache this information, type information does not change that often.
    MetricEventType type = resolveMetricType(name, tagCopy, startMs, endMs);

    return switch (type) {
      case HISTO -> getHistogramSeries(name, tagCopy, startMs, endMs);
      case SUM -> getSumSeries(name, tagCopy, startMs, endMs);
      case GAUGE -> getGaugeSeries(name, tagCopy, startMs, endMs);
    };
  }

  private MetricEventType resolveMetricType(
      String metric, Map<String, String> tags, long startMs, long endMs) {
    TemplateOutput output = new StringOutput();
    templateEngine.render(
        GET_METRIC_EVENT_TYPE_EXACT_MATCH,
        ChMetricEventTypeQueryTemplate.builder()
            .table(ChConstants.TBL_METRIC_EVENTS_META)
            .metric(ChSqlEscaper.escapeLiteral(metric))
            .startMs(startMs)
            .endMs(endMs)
            .tags(ChSqlEscaper.escapeTags(tags))
            .build(),
        output);
    var query = output.toString();
    List<GenericRecord> records = client.queryAll(query);
    if (records.isEmpty()) {
      return MetricEventType.GAUGE;
    }
    String eventType = records.getFirst().getString("event_type");
    return MetricEventType.valueOf(eventType);
  }

  private GaugeScan getGaugeSeries(
      String metric, Map<String, String> tags, long startMs, long endMs) {
    TemplateOutput output = new StringOutput();
    templateEngine.render(
        GET_GAUGE_RAW_SAMPLES_EXACT_MATCH,
        ChGetGaugeRawQueryTemplate.builder()
            .table(ChConstants.TBL_GAUGES)
            .metric(ChSqlEscaper.escapeLiteral(metric))
            .startMs(startMs)
            .endMs(endMs)
            .tags(ChSqlEscaper.escapeTags(tags))
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

  private Scan getSumSeries(String metric, Map<String, String> tags, long startMs, long endMs) {
    var delta = scanSumSamples(metric, tags, startMs, endMs, "DELTA");
    if (!delta.isEmpty()) {
      return toSumScan(metric, delta, false);
    }
    var cumulative = scanSumSamples(metric, tags, startMs, endMs, "CUMULATIVE");
    return toSumScan(metric, cumulative, true);
  }

  private List<SumPoint> scanSumSamples(
      String metric, Map<String, String> tags, long startMs, long endMs, String type) {
    TemplateOutput output = new StringOutput();
    templateEngine.render(
        GET_SUM_SAMPLES_EXACT_MATCH,
        ChGetSumQueryTemplate.builder()
            .table(ChConstants.TBL_SUM)
            .metric(ChSqlEscaper.escapeLiteral(metric))
            .tags(ChSqlEscaper.escapeTags(tags))
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
              record.getLong("ts_start_ms"), record.getLong("ts_end_ms"), record.getLong("value")));
    }
    points.sort(Comparator.comparingLong(SumPoint::endMs));
    return points;
  }

  private SumScan toSumScan(String metric, List<SumPoint> points, boolean cumulative) {
    var ts = new ArrayList<Long>(points.size());
    var counts = new ArrayList<Integer>(points.size());
    Long prev = null;
    for (var p : points) {
      ts.add(p.endMs());
      long val = p.value();
      long delta = val;
      if (cumulative && prev != null) {
        delta = val - prev;
        if (delta < 0) delta = val; // counter reset
      }
      counts.add(clampToInt(delta));
      prev = val;
    }
    return SumScan.builder().universalPath(metric).ts(ts).counts(counts).windowSize(0).build();
  }

  private Scan getHistogramSeries(
      String metric, Map<String, String> tags, long startMs, long endMs) {
    var delta = scanHistoSamples(metric, tags, startMs, endMs, "DELTA");
    if (!delta.isEmpty()) {
      return new HistogramSeries(metric, delta);
    }
    var cumulative = scanHistoSamples(metric, tags, startMs, endMs, "CUMULATIVE");
    if (cumulative.isEmpty()) {
      return new HistogramSeries(metric, List.of());
    }
    return new HistogramSeries(metric, toDeltaHistos(cumulative));
  }

  private List<ExplicitHistogramSample> scanHistoSamples(
      String metric, Map<String, String> tags, long startMs, long endMs, String type) {
    TemplateOutput output = new StringOutput();
    templateEngine.render(
        GET_HISTO_SAMPLES_EXACT_MATCH,
        ChGetHistoQueryTemplate.builder()
            .table(ChConstants.TBL_HISTOS)
            .metric(ChSqlEscaper.escapeLiteral(metric))
            .tags(ChSqlEscaper.escapeTags(tags))
            .histoType(type)
            .ts(startMs)
            .te(endMs)
            .build(),
        output);
    var query = output.toString();
    List<GenericRecord> records = client.queryAll(query);
    var points = new ArrayList<ExplicitHistogramSample>(records.size());
    for (var record : records) {
      float[] buckets = readFloatArray(record, "buckets");
      int[] counts = readIntArray(record, "counts");
      points.add(
          new ExplicitHistogramSample(
              record.getLong("ts_start_ms"),
              record.getLong("ts_end_ms"),
              Temporality.valueOf(type),
              buckets,
              counts,
              Float.NaN,
              totalCount(counts)));
    }
    points.sort(Comparator.comparingLong(ExplicitHistogramSample::endMs));
    return points;
  }

  private List<ExplicitHistogramSample> toDeltaHistos(
      List<ExplicitHistogramSample> cumulative) {
    var out = new ArrayList<ExplicitHistogramSample>(cumulative.size());
    ExplicitHistogramSample prev = null;
    for (var p : cumulative) {
      int[] counts = p.counts();
      if (prev != null && counts != null && prev.counts() != null) {
        int[] prevCounts = prev.counts();
        if (prevCounts.length == counts.length) {
          int[] delta = new int[counts.length];
          for (int i = 0; i < counts.length; i++) {
            int d = counts[i] - prevCounts[i];
            delta[i] = d < 0 ? counts[i] : d;
          }
          out.add(
              new ExplicitHistogramSample(
                  p.startMs(),
                  p.endMs(),
                  Temporality.DELTA,
                  p.upperBounds(),
                  delta,
                  Float.NaN,
                  totalCount(delta)));
        } else {
          out.add(p);
        }
      } else {
        out.add(p);
      }
      prev = p;
    }
    return out;
  }

  private static float[] readFloatArray(GenericRecord record, String key) {
    try {
      return record.getFloatArray(key);
    } catch (Exception e) {
      var list = record.getList(key);
      if (list == null) {
        return new float[0];
      }
      float[] arr = new float[list.size()];
      for (int i = 0; i < list.size(); i++) {
        arr[i] = ((Number) list.get(i)).floatValue();
      }
      return arr;
    }
  }

  private static int[] readIntArray(GenericRecord record, String key) {
    try {
      return record.getIntArray(key);
    } catch (Exception e) {
      var list = record.getList(key);
      if (list == null) {
        return new int[0];
      }
      int[] arr = new int[list.size()];
      for (int i = 0; i < list.size(); i++) {
        arr[i] = ((Number) list.get(i)).intValue();
      }
      return arr;
    }
  }

  private static int totalCount(int[] counts) {
    int total = 0;
    for (int count : counts) total += count;
    return total;
  }

  private static int clampToInt(long value) {
    if (value > Integer.MAX_VALUE) return Integer.MAX_VALUE;
    if (value < Integer.MIN_VALUE) return Integer.MIN_VALUE;
    return (int) value;
  }

  private enum MetricEventType {
    GAUGE,
    HISTO,
    SUM
  }

  private record SumPoint(long startMs, long endMs, long value) {}
}

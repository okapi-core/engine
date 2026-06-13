/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.metrics.otel;

import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.metrics.v1.*;
import org.okapi.bytes.OkapiBytes;
import org.okapi.collections.OkapiLists;
import org.okapi.rest.metrics.Exemplar;
import org.okapi.rest.metrics.ExportMetricsRequest;
import org.okapi.rest.metrics.MetricType;
import org.okapi.rest.metrics.payloads.ExponentialHisto;
import org.okapi.rest.metrics.payloads.ExponentialHistoPoint;
import org.okapi.rest.metrics.payloads.Gauge.GaugeBuilder;
import org.okapi.rest.metrics.payloads.Histo;
import org.okapi.rest.metrics.payloads.HistoPoint;
import org.okapi.rest.metrics.payloads.SUM_TEMPORALITY;
import org.okapi.rest.metrics.payloads.SumPoint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

import static org.okapi.clock.OkapiTimeUtils.nanosToMillis;
import static org.okapi.metrics.otel.OtelValueDecoders.*;

/**
 * Converts OTLP ExportMetricsServiceRequest (protobuf) into Okapi ExportMetricsRequest(s).
 *
 * <p>Notes: - Handles only Gauge, Sum, and Histogram types. - Groups datapoints by the full tag set
 * (resource + scope + datapoint attributes). - Converts OTLP timestamps (nanos) to epoch
 * milliseconds expected by Okapi.
 */
@Component
public final class OtelConverter {

  private final OtelConverterConfig config;

  public OtelConverter() {
    this(OtelConverterConfig.defaultConfig());
  }

  @Autowired
  public OtelConverter(OtelConverterConfig config) {
    this.config = config == null ? OtelConverterConfig.defaultConfig() : config;
  }

  private Map<String, String> toTagsMap(List<KeyValue> attrs) {
    Map<String, String> out = new LinkedHashMap<>();
    attrs.stream()
        .filter(Objects::nonNull)
        .filter(kv -> !kv.getKey().isEmpty())
        .filter(kv -> !isExcludedTag(kv.getKey()))
        .forEach(kv -> out.put(kv.getKey(), anyValueToString(kv.getValue())));
    return out;
  }

  private static String canonicalKey(Map<String, String> tags) {
    return tags.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .map(e -> e.getKey() + "=" + Objects.toString(e.getValue(), ""))
        .collect(Collectors.joining("|"));
  }

  private boolean isExcludedTag(String key) {
    if (key == null || key.isEmpty() || config.getExcludeTags().contains(key)) return true;
    for (String prefix : config.getExcludeTagPrefixes()) {
      if (key.startsWith(prefix)) return true;
    }
    return false;
  }

  /** Convert an OTLP request into one or more Okapi requests. */
  public List<ExportMetricsRequest> toOkapiRequests(ExportMetricsServiceRequest otlp) {
    if (otlp == null) return Collections.emptyList();
    List<ExportMetricsRequest> out = new ArrayList<>();
    for (ResourceMetrics rm : otlp.getResourceMetricsList()) {
      out.addAll(convertResourceMetrics(rm));
    }
    return out;
  }

  private List<ExportMetricsRequest> convertResourceMetrics(ResourceMetrics rm) {
    List<ExportMetricsRequest> out = new ArrayList<>();
    for (ScopeMetrics sm : rm.getScopeMetricsList()) {
      out.addAll(convertScopeMetrics(sm));
    }
    return out;
  }

  private List<ExportMetricsRequest> convertScopeMetrics(ScopeMetrics sm) {
    List<ExportMetricsRequest> out = new ArrayList<>();
    for (Metric m : sm.getMetricsList()) {
      out.addAll(convertMetric(m));
    }
    return out;
  }

  private List<ExportMetricsRequest> convertMetric(Metric m) {
    if (m.hasGauge()) {
      return convertGauge(m);
    } else if (m.hasSum()) {
      return convertSum(m);
    } else if (m.hasHistogram()) {
      return convertHistogram(m);
    } else if (m.hasExponentialHistogram()) {
      return convertExponentialHistogram(m);
    }
    return Collections.emptyList();
  }

  public Exemplar fromOtelExemplarToOkapiExemplar(
      String metric,
      Map<String, String> tags,
      io.opentelemetry.proto.metrics.v1.Exemplar otelExemplar) {
    var val = OtelValueDecoders.decodeExemplarAsDouble(otelExemplar);
    var attributes =
        otelExemplar.getFilteredAttributesList().stream()
            .map(OtelValueDecoders::decodeKv)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .toList();
    var traceId = OkapiBytes.encodeAsHex(otelExemplar.getTraceId().toByteArray());
    var spanId = OkapiBytes.encodeAsHex(otelExemplar.getSpanId().toByteArray());
    var tsNanos = otelExemplar.getTimeUnixNano();
    var builder =
        Exemplar.builder()
            .spanId(spanId)
            .tsNanos(tsNanos)
            .metric(metric)
            .tags(tags)
            .traceId(traceId)
            .kv(attributes);
    val.ifPresent(builder::measurement);
    return builder.build();
  }

  public List<Exemplar> collectExemplar(
      String metric, Map<String, String> tags, NumberDataPoint numberDataPoint) {
    return numberDataPoint.getExemplarsList().stream()
        .map((e) -> this.fromOtelExemplarToOkapiExemplar(metric, tags, e))
        .toList();
  }

  public List<Exemplar> collectExemplar(
      String metric, Map<String, String> tags, HistogramDataPoint histogramDataPoint) {
    return histogramDataPoint.getExemplarsList().stream()
        .map((e) -> this.fromOtelExemplarToOkapiExemplar(metric, tags, e))
        .toList();
  }

  private List<ExportMetricsRequest> convertGauge(Metric metric) {
    var g = metric.getGauge();
    var metricName = metric.getName();
    // Group points by tags
    Map<String, Map<String, String>> tagKeyToTags = new HashMap<>();
    Map<String, List<Long>> tsByKey = new HashMap<>();
    Map<String, List<Double>> valsByKey = new HashMap<>();
    Map<String, List<Exemplar>> exemplarsByKey = new HashMap<>();
    for (var p : g.getDataPointsList()) {
      Map<String, String> tags = toTagsMap(p.getAttributesList());
      String key = canonicalKey(tags);
      tagKeyToTags.putIfAbsent(key, tags);
      tsByKey
          .computeIfAbsent(key, OkapiLists::keyToEmptyArrayList)
          .add(nanosToMillis(p.getTimeUnixNano()));
      valsByKey
          .computeIfAbsent(key, OkapiLists::keyToEmptyArrayList)
          .add(OtelValueDecoders.extractNumberAsDouble(p));
      var exemplar = collectExemplar(metricName, tags, p);
      exemplarsByKey.computeIfAbsent(key, OkapiLists::keyToEmptyArrayList).addAll(exemplar);
    }

    List<ExportMetricsRequest> out = new ArrayList<>();
    for (String key : tagKeyToTags.keySet()) {
      var tsList = tsByKey.getOrDefault(key, List.of());
      var valList = valsByKey.getOrDefault(key, List.of());
      GaugeBuilder gauge =
          org.okapi.rest.metrics.payloads.Gauge.builder()
              .ts(tsList)
              .exemplars(Collections.unmodifiableList(exemplarsByKey.get(key)))
              .value(valList);
      out.add(
          ExportMetricsRequest.builder()
              .unit(metric.getUnit())
              .metricName(metricName)
              .tags(tagKeyToTags.get(key))
              .type(MetricType.GAUGE)
              .gauge(gauge.build())
              .build());
    }
    return out;
  }

  private List<ExportMetricsRequest> convertSum(Metric metric) {
    var s = metric.getSum();
    SUM_TEMPORALITY SUMTYPE =
        switch (s.getAggregationTemporality()) {
          case AGGREGATION_TEMPORALITY_CUMULATIVE -> SUM_TEMPORALITY.CUMULATIVE;
          case AGGREGATION_TEMPORALITY_DELTA -> SUM_TEMPORALITY.DELTA;
          case AGGREGATION_TEMPORALITY_UNSPECIFIED -> SUM_TEMPORALITY.DELTA; // fallback
          default -> SUM_TEMPORALITY.DELTA;
        };

    // Group datapoints by tags
    Map<String, Map<String, String>> tagKeyToTags = new HashMap<>();
    Map<String, List<SumPoint>> ptsByKey = new HashMap<>();
    for (NumberDataPoint p : s.getDataPointsList()) {
      Map<String, String> tags = toTagsMap(p.getAttributesList());
      String key = canonicalKey(tags);
      tagKeyToTags.putIfAbsent(key, tags);
      SumPoint pt = new SumPoint();
      pt.setStart(nanosToMillis(p.getStartTimeUnixNano()));
      pt.setEnd(nanosToMillis(p.getTimeUnixNano()));
      pt.setSum(OtelValueDecoders.extractNumberAsDouble(p));
      ptsByKey.computeIfAbsent(key, OkapiLists::keyToEmptyArrayList).add(pt);
    }

    List<ExportMetricsRequest> out = new ArrayList<>();
    for (String key : tagKeyToTags.keySet()) {
      var points = ptsByKey.getOrDefault(key, List.of());
      var sumPayload =
          org.okapi.rest.metrics.payloads.Sum.builder()
              .temporality(SUMTYPE)
              .sumPoints(points)
              .build();
      out.add(
          ExportMetricsRequest.builder()
              .unit(metric.getUnit())
              .metricName(metric.getName())
              .tags(tagKeyToTags.get(key))
              .type(MetricType.COUNTER)
              .sum(sumPayload)
              .build());
    }
    return out;
  }

  private List<ExportMetricsRequest> convertHistogram(Metric metric) {
    // Group datapoints by tags
    var otelHisto = metric.getHistogram();
    Map<String, Map<String, String>> tagKeyToTags = new HashMap<>();
    Map<String, List<HistoPoint>> ptsByKey = new HashMap<>();
    Map<String, List<Exemplar>> exemplarsByKey = new HashMap<>();
    for (var p : otelHisto.getDataPointsList()) {
      Map<String, String> tags = toTagsMap(p.getAttributesList());
      String key = canonicalKey(tags);
      tagKeyToTags.putIfAbsent(key, tags);

      HistoPoint pt = new HistoPoint();
      pt.setStart(nanosToMillis(p.getStartTimeUnixNano()));
      pt.setEnd(nanosToMillis(p.getTimeUnixNano()));
      var translatedTemporality =
          switch (otelHisto.getAggregationTemporality()) {
            case AGGREGATION_TEMPORALITY_DELTA -> HistoPoint.TEMPORALITY.DELTA;
            case AGGREGATION_TEMPORALITY_CUMULATIVE -> HistoPoint.TEMPORALITY.CUMULATIVE;
            case AGGREGATION_TEMPORALITY_UNSPECIFIED -> HistoPoint.TEMPORALITY.DELTA; // fallback
            default -> HistoPoint.TEMPORALITY.DELTA;
          };
      pt.setTemporality(translatedTemporality);

      // Bounds: double[] -> float[]
      float[] buckets = new float[p.getExplicitBoundsCount()];
      for (int i = 0; i < buckets.length; i++) {
        buckets[i] = (float) p.getExplicitBounds(i);
      }
      pt.setBuckets(buckets);
      pt.setSum(p.hasSum() ? p.getSum() : null);
      pt.setCount(p.getCount());

      // Counts: protobuf exposes uint64 as signed long; preserve the available range end to end.
      long[] counts = new long[p.getBucketCountsCount()];
      for (int i = 0; i < counts.length; i++) {
        counts[i] = p.getBucketCounts(i);
      }
      pt.setBucketCounts(counts);
      ptsByKey.computeIfAbsent(key, OkapiLists::keyToEmptyArrayList).add(pt);

      var exemplars = collectExemplar(metric.getName(), tags, p);
      exemplarsByKey.computeIfAbsent(key, OkapiLists::keyToEmptyArrayList).addAll(exemplars);
    }

    List<ExportMetricsRequest> out = new ArrayList<>();
    for (String key : tagKeyToTags.keySet()) {
      Histo histo = Histo.builder().histoPoints(ptsByKey.getOrDefault(key, List.of())).build();
      out.add(
          ExportMetricsRequest.builder()
              .unit(metric.getUnit())
              .metricName(metric.getName())
              .tags(tagKeyToTags.get(key))
              .type(MetricType.HISTO)
              .histo(histo)
              .build());
    }
    return out;
  }

  private List<ExportMetricsRequest> convertExponentialHistogram(Metric metric) {
    var histogram = metric.getExponentialHistogram();
    var temporality =
        switch (histogram.getAggregationTemporality()) {
          case AGGREGATION_TEMPORALITY_CUMULATIVE -> HistoPoint.TEMPORALITY.CUMULATIVE;
          case AGGREGATION_TEMPORALITY_DELTA, AGGREGATION_TEMPORALITY_UNSPECIFIED ->
              HistoPoint.TEMPORALITY.DELTA;
          default -> HistoPoint.TEMPORALITY.DELTA;
        };
    Map<String, Map<String, String>> tagKeyToTags = new HashMap<>();
    Map<String, List<ExponentialHistoPoint>> pointsByKey = new HashMap<>();
    for (var point : histogram.getDataPointsList()) {
      Map<String, String> tags = toTagsMap(point.getAttributesList());
      String key = canonicalKey(tags);
      tagKeyToTags.putIfAbsent(key, tags);
      pointsByKey
          .computeIfAbsent(key, OkapiLists::keyToEmptyArrayList)
          .add(
              ExponentialHistoPoint.builder()
                  .start(nanosToMillis(point.getStartTimeUnixNano()))
                  .end(nanosToMillis(point.getTimeUnixNano()))
                  .temporality(temporality)
                  .scale(point.getScale())
                  .zeroThreshold(point.getZeroThreshold())
                  .zeroCount(point.getZeroCount())
                  .positiveOffset(point.getPositive().getOffset())
                  .positiveCounts(toLongArray(point.getPositive().getBucketCountsList()))
                  .negativeOffset(point.getNegative().getOffset())
                  .negativeCounts(toLongArray(point.getNegative().getBucketCountsList()))
                  .sum(point.hasSum() ? point.getSum() : null)
                  .count(point.getCount())
                  .build());
    }
    List<ExportMetricsRequest> out = new ArrayList<>();
    for (String key : tagKeyToTags.keySet()) {
      out.add(
          ExportMetricsRequest.builder()
              .unit(metric.getUnit())
              .metricName(metric.getName())
              .tags(tagKeyToTags.get(key))
              .type(MetricType.HISTO)
              .exponentialHisto(
                  ExponentialHisto.builder().histoPoints(pointsByKey.get(key)).build())
              .build());
    }
    return out;
  }

  private static long[] toLongArray(List<Long> counts) {
    long[] values = new long[counts.size()];
    for (int i = 0; i < counts.size(); i++) values[i] = counts.get(i);
    return values;
  }
}

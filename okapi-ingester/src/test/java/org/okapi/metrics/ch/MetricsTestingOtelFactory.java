/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.metrics.ch;

import com.google.protobuf.ByteString;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.metrics.v1.*;
import io.opentelemetry.proto.resource.v1.Resource;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import org.okapi.bytes.OkapiBytes;
import org.okapi.otelshorthand.OtelShortHands;
import org.okapi.timeutils.TimeUtils;

@AllArgsConstructor
public class MetricsTestingOtelFactory {

  String testSession;

  public ExportMetricsServiceRequest buildGaugeRequest(
      String resourceName, String metricName, List<Long> timestampsMs, List<Double> values) {
    return buildGaugeRequest(resourceName, metricName, timestampsMs, values, "unit");
  }

  public ExportMetricsServiceRequest buildGaugeRequest(
      String resourceName,
      String metricName,
      List<Long> timestampsMs,
      List<Double> values,
      String unit) {
    var gaugeBuilder = Gauge.newBuilder();
    for (int i = 0; i < timestampsMs.size(); i++) {
      gaugeBuilder.addDataPoints(
          NumberDataPoint.newBuilder()
              .setTimeUnixNano(TimeUtils.millisToNanos(timestampsMs.get(i)))
              .setStartTimeUnixNano(0)
              .addAttributes(OtelShortHands.keyValue("env", "dev"))
              .addAttributes(OtelShortHands.keyValue("test-session", testSession))
              .setAsDouble(values.get(i))
              .build());
    }
    Metric metric =
        Metric.newBuilder()
            .setUnit(unit)
            .setName(metricName)
            .setGauge(gaugeBuilder.build())
            .build();
    var scopeMetrics = ScopeMetrics.newBuilder().addMetrics(metric).build();
    var resource =
        Resource.newBuilder()
            .addAttributes(OtelShortHands.keyValue("service.name", resourceName))
            .build();
    var resourceMetrics =
        ResourceMetrics.newBuilder().setResource(resource).addScopeMetrics(scopeMetrics).build();
    return ExportMetricsServiceRequest.newBuilder().addResourceMetrics(resourceMetrics).build();
  }

  public NumberDataPoint buildGaugePoint(
      String traceId,
      String spanId,
      Map<String, String> tags,
      Map<String, String> attributes,
      long tsMs,
      Double value) {
    var exemplar =
        Exemplar.newBuilder()
            .addAllFilteredAttributes(OtelShortHands.keyValues(attributes))
            .setSpanId(ByteString.copyFrom(OkapiBytes.decodeHex(spanId)))
            .setTraceId(ByteString.copyFrom(OkapiBytes.decodeHex(traceId)))
            .setTimeUnixNano(TimeUtils.millisToNanos(tsMs))
            .setAsDouble(value)
            .build();
    return NumberDataPoint.newBuilder()
        .addAllAttributes(OtelShortHands.keyValues(tags))
        .setTimeUnixNano(TimeUtils.millisToNanos(tsMs))
        .setStartTimeUnixNano(0)
        .setAsDouble(value)
        .addExemplars(exemplar)
        .build();
  }

  public ExportMetricsServiceRequest buildGaugeWithExemplarData(
      String metric,
      Map<String, String> tags,
      List<Long> ts,
      List<Double> values,
      String spanId,
      String traceId) {
    if (ts.size() != values.size()) {
      throw new IllegalArgumentException("timestamps and values must be the same size");
    }
    var gaugeBuilder = Gauge.newBuilder();
    for (int i = 0; i < ts.size(); i++) {
      var pointTags = new java.util.LinkedHashMap<String, String>(tags);
      var attributes = new java.util.LinkedHashMap<String, String>();
      attributes.put("exemplar.index", Integer.toString(i));
      attributes.put("exemplar.source", "unit-test");
      attributes.put("test-session", testSession);
      var point =
          buildGaugePoint(
              withSuffixHex(traceId, i),
              withSuffixHex(spanId, i),
              pointTags,
              attributes,
              ts.get(i),
              values.get(i));
      gaugeBuilder.addDataPoints(point);
    }
    Metric metricProto = Metric.newBuilder().setName(metric).setGauge(gaugeBuilder.build()).build();
    var scopeMetrics = ScopeMetrics.newBuilder().addMetrics(metricProto).build();
    var resource = Resource.newBuilder().build();
    var resourceMetrics =
        ResourceMetrics.newBuilder().setResource(resource).addScopeMetrics(scopeMetrics).build();
    return ExportMetricsServiceRequest.newBuilder().addResourceMetrics(resourceMetrics).build();
  }

  static String withSuffixHex(String baseHex, int index) {
    if (index == 0) {
      return baseHex;
    }
    if (baseHex.length() < 2) {
      throw new IllegalArgumentException("base hex must be at least 2 chars");
    }
    var suffix = Integer.toHexString(index);
    var suffixLength = Math.max(2, suffix.length());
    if (suffixLength > baseHex.length()) {
      throw new IllegalArgumentException("index does not fit in base hex value");
    }
    return baseHex.substring(0, baseHex.length() - suffixLength)
        + String.format("%0" + suffixLength + "x", index);
  }
}

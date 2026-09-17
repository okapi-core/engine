/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.oscar.integ.corpus;

import static org.okapi.oscar.integ.OtelHelpers.*;

import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.metrics.v1.*;
import io.opentelemetry.proto.trace.v1.Span;
import io.opentelemetry.proto.trace.v1.Status;
import java.util.ArrayList;
import java.util.List;
import org.okapi.ingester.client.IngesterClient;

public class SingleStepFlowCorpus implements Corpus {

  public static final List<String> METRIC_PATHS =
      List.of("checkout.http.requests", "checkout.jvm.heap", "checkout.cpu.usage");
  public static final String CHECKOUT_SERVICE = "checkout";
  public static final String PAYMENT_SERVICE = "payment-service";
  public static final String ORDER_SERVICE = "order-service";
  public static final String GATEWAY_SERVICE = "api-gateway";
  public static final String GATEWAY_OPERATION = "POST /checkout";

  private final IngesterClient ingesterClient;

  public SingleStepFlowCorpus(IngesterClient ingesterClient) {
    this.ingesterClient = ingesterClient;
  }

  @Override
  public void seed() {
    CorpusAwait.truncateAll();
    seedMetrics();
    seedErrorTraces();
    seedDbTraces();
    seedGatewayTraces();
    CorpusAwait.awaitMetric(ingesterClient, METRIC_PATHS.get(0));
  }

  private void seedMetrics() {
    long nowNs = System.currentTimeMillis() * 1_000_000L;

    // checkout.http.requests is a counter (Sum), not a Gauge
    seedSumMetric("checkout.http.requests", nowNs, 1200.0);

    // JVM heap and CPU are gauges
    for (String metricName : List.of("checkout.jvm.heap", "checkout.cpu.usage")) {
      seedGaugeMetric(metricName, nowNs, 42.0);
    }
  }

  private void seedGaugeMetric(String metricName, long nowNs, double value) {
    var dataPoint =
        NumberDataPoint.newBuilder()
            .setTimeUnixNano(nowNs)
            .addAttributes(kv("env", "prod"))
            .setAsDouble(value)
            .build();
    var metric =
        Metric.newBuilder()
            .setName(metricName)
            .setGauge(Gauge.newBuilder().addDataPoints(dataPoint).build())
            .build();
    ingest(metric);
  }

  private void seedSumMetric(String metricName, long nowNs, double value) {
    var dataPoint =
        NumberDataPoint.newBuilder()
            .setTimeUnixNano(nowNs)
            .addAttributes(kv("env", "prod"))
            .setAsDouble(value)
            .build();
    var metric =
        Metric.newBuilder()
            .setName(metricName)
            .setSum(
                Sum.newBuilder()
                    .addDataPoints(dataPoint)
                    .setAggregationTemporality(
                        AggregationTemporality.AGGREGATION_TEMPORALITY_CUMULATIVE)
                    .build())
            .build();
    ingest(metric);
  }

  private void ingest(Metric metric) {
    var resourceMetrics =
        ResourceMetrics.newBuilder()
            .setResource(serviceResource(CHECKOUT_SERVICE))
            .addScopeMetrics(ScopeMetrics.newBuilder().addMetrics(metric).build())
            .build();
    ingesterClient.ingestOtelMetrics(
        ExportMetricsServiceRequest.newBuilder().addResourceMetrics(resourceMetrics).build());
  }

  private void seedErrorTraces() {
    long startNs = System.currentTimeMillis() * 1_000_000L;
    var span =
        Span.newBuilder()
            .setTraceId(traceId())
            .setSpanId(spanId())
            .setName("POST /payments")
            .setKind(Span.SpanKind.SPAN_KIND_SERVER)
            .setStartTimeUnixNano(startNs)
            .setEndTimeUnixNano(startNs + 50_000_000L)
            .addAttributes(kv("http.status_code", 500))
            .setStatus(Status.newBuilder().setCode(Status.StatusCode.STATUS_CODE_ERROR).build())
            .build();
    ingesterClient.ingestOtelTraces(traceRequest(resourceSpans(PAYMENT_SERVICE, List.of(span))));
  }

  private void seedDbTraces() {
    long startNs = System.currentTimeMillis() * 1_000_000L;
    var span =
        Span.newBuilder()
            .setTraceId(traceId())
            .setSpanId(spanId())
            .setName("SELECT orders")
            .setKind(Span.SpanKind.SPAN_KIND_CLIENT)
            .setStartTimeUnixNano(startNs)
            .setEndTimeUnixNano(startNs + 30_000_000L)
            .addAttributes(kv("db.system", "postgresql"))
            .addAttributes(kv("db.name", "orders"))
            .setStatus(Status.newBuilder().setCode(Status.StatusCode.STATUS_CODE_OK).build())
            .build();
    ingesterClient.ingestOtelTraces(traceRequest(resourceSpans(ORDER_SERVICE, List.of(span))));
  }

  private void seedGatewayTraces() {
    long baseNs = System.currentTimeMillis() * 1_000_000L;
    var spans = new ArrayList<Span>();

    for (int i = 0; i < 3; i++) {
      long startNs = baseNs + (long) i * 200_000_000L;
      spans.add(
          Span.newBuilder()
              .setTraceId(traceId())
              .setSpanId(spanId())
              .setName(GATEWAY_OPERATION)
              .setKind(Span.SpanKind.SPAN_KIND_SERVER)
              .setStartTimeUnixNano(startNs)
              .setEndTimeUnixNano(startNs + 100_000_000L + (long) i * 50_000_000L)
              .addAttributes(kv("http.status_code", 200))
              .setStatus(Status.newBuilder().setCode(Status.StatusCode.STATUS_CODE_OK).build())
              .build());
    }

    long errStart = baseNs + 800_000_000L;
    spans.add(
        Span.newBuilder()
            .setTraceId(traceId())
            .setSpanId(spanId())
            .setName(GATEWAY_OPERATION)
            .setKind(Span.SpanKind.SPAN_KIND_SERVER)
            .setStartTimeUnixNano(errStart)
            .setEndTimeUnixNano(errStart + 200_000_000L)
            .addAttributes(kv("http.status_code", 503))
            .setStatus(Status.newBuilder().setCode(Status.StatusCode.STATUS_CODE_ERROR).build())
            .build());

    ingesterClient.ingestOtelTraces(traceRequest(resourceSpans(GATEWAY_SERVICE, spans)));
  }
}

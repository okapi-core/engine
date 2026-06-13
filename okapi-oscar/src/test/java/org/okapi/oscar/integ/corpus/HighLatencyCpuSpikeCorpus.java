package org.okapi.oscar.integ.corpus;

import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.metrics.v1.*;
import io.opentelemetry.proto.resource.v1.Resource;
import io.opentelemetry.proto.trace.v1.Span;
import io.opentelemetry.proto.trace.v1.Status;
import org.okapi.ingester.client.IngesterClient;

import java.util.List;

import static org.okapi.oscar.integ.OtelHelpers.*;

public class HighLatencyCpuSpikeCorpus implements Corpus {

  public static final String POSTGRES_HOST = "postgres-host-01";
  public static final String CHECKOUT_SERVICE = "checkout";
  public static final String CPU_METRIC_NAME = "container.cpu.percent";
  public static final double HIGH_CPU_VALUE = 95.0;
  public static final long SLOW_SPAN_DURATION_MS = 800L;

  private final IngesterClient ingesterClient;

  public HighLatencyCpuSpikeCorpus(IngesterClient ingesterClient) {
    this.ingesterClient = ingesterClient;
  }

  @Override
  public void seed() {
    CorpusAwait.truncateAll();
    seedSlowTrace();
    seedCpuMetrics();
    CorpusAwait.awaitMetric(ingesterClient, CPU_METRIC_NAME);
  }

  private void seedSlowTrace() {
    long startNs = System.currentTimeMillis() * 1_000_000L;
    long endNs = startNs + SLOW_SPAN_DURATION_MS * 1_000_000L;
    var span =
        Span.newBuilder()
            .setTraceId(traceId())
            .setSpanId(spanId())
            .setName("POST /checkout")
            .setKind(Span.SpanKind.SPAN_KIND_SERVER)
            .setStartTimeUnixNano(startNs)
            .setEndTimeUnixNano(endNs)
            .addAttributes(kv("server.name", POSTGRES_HOST))
            .setStatus(Status.newBuilder().setCode(Status.StatusCode.STATUS_CODE_OK).build())
            .build();
    ingesterClient.ingestOtelTraces(traceRequest(resourceSpans(CHECKOUT_SERVICE, List.of(span))));
  }

  private void seedCpuMetrics() {
    long nowNs = System.currentTimeMillis() * 1_000_000L;
    var dataPoint =
        NumberDataPoint.newBuilder()
            .setTimeUnixNano(nowNs)
            .addAttributes(kv("host.name", POSTGRES_HOST))
            .addAttributes(kv("container.name", "postgres"))
            .setAsDouble(HIGH_CPU_VALUE)
            .build();
    var metric =
        Metric.newBuilder()
            .setName(CPU_METRIC_NAME)
            .setGauge(Gauge.newBuilder().addDataPoints(dataPoint).build())
            .build();
    var resourceMetrics =
        ResourceMetrics.newBuilder()
            .setResource(
                Resource.newBuilder().addAttributes(kv("host.name", POSTGRES_HOST)).build())
            .addScopeMetrics(ScopeMetrics.newBuilder().addMetrics(metric).build())
            .build();
    ingesterClient.ingestOtelMetrics(
        ExportMetricsServiceRequest.newBuilder().addResourceMetrics(resourceMetrics).build());
  }
}

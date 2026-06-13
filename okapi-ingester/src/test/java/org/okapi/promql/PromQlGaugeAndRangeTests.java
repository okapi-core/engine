/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.clickhouse.client.api.Client;
import com.google.inject.Guice;
import com.google.inject.Injector;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.metrics.v1.Gauge;
import io.opentelemetry.proto.metrics.v1.Metric;
import io.opentelemetry.proto.metrics.v1.NumberDataPoint;
import io.opentelemetry.proto.metrics.v1.ResourceMetrics;
import io.opentelemetry.proto.metrics.v1.ScopeMetrics;
import io.opentelemetry.proto.resource.v1.Resource;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.okapi.Constants;
import org.okapi.ch.CreateChTablesSpec;
import org.okapi.metrics.ch.ChConstants;
import org.okapi.metrics.ch.ChMetricsIngester;
import org.okapi.metrics.ch.ChMetricsWalConsumerDriver;
import org.okapi.metrics.pojos.results.GaugeScan;
import org.okapi.promql.eval.InstantVectorResult;
import org.okapi.promql.eval.VectorData;
import org.okapi.promql.eval.ts.RESOLUTION;
import org.okapi.promql.query.PromQlQueryProcessor;
import org.okapi.promql.runtime.TsClientFactory;
import org.okapi.testmodules.guice.TestChMetricsModule;

public class PromQlGaugeAndRangeTests {

  @TempDir Path tempDir;

  private Injector injector;
  private Client client;
  private String testSession;

  @BeforeEach
  void setup() {
    testSession = UUID.randomUUID().toString();
    injector = Guice.createInjector(new TestChMetricsModule(tempDir.resolve("wal"), 16));
    client = injector.getInstance(Client.class);
    CreateChTablesSpec.migrate(client);
    client.queryAll("TRUNCATE TABLE IF EXISTS " + ChConstants.TBL_GAUGES);
    client.queryAll("TRUNCATE TABLE IF EXISTS " + ChConstants.TBL_METRIC_EVENTS_META);
  }

  @Test
  void instantGaugeQueryReturnsLatestSampleAtStep() throws Exception {
    var ingester = injector.getInstance(ChMetricsIngester.class);
    var driver = injector.getInstance(ChMetricsWalConsumerDriver.class);
    var promql = injector.getInstance(PromQlQueryProcessor.class);

    var resource = "svc-gauge-" + UUID.randomUUID();
    var metric = "cpu_usage";
    var tags = Map.of("env", "dev", "test-session", testSession);

    ingester.ingestOtelProtobuf(
        buildGaugeRequest(resource, metric, tags, List.of(1_000L, 2_000L), List.of(1.0, 2.0)));
    driver.onTick();

    var result = promql.queryRange(Constants.DEFAULT_TENANT, metric, 1_000L, 2_000L, 1_000L);
    assertNotNull(result);
    var matrix = ((InstantVectorResult) result).toMatrix();
    assertFalse(matrix.isEmpty());

    List<VectorData.Sample> samples = matrix.values().iterator().next();
    assertEquals(2, samples.size());
    assertEquals(1_000L, samples.get(0).ts());
    assertEquals(1.0f, samples.get(0).value());
    assertEquals(2_000L, samples.get(1).ts());
    assertEquals(2.0f, samples.get(1).value());
  }

  @Test
  void instantGaugeQueryAtNearEpochTimestampDoesNotOverflow() throws Exception {
    // Regression: evalInstantize subtracted STALENESS_MS (300_000 ms) from startMs without
    // clamping, producing a negative timestamp that ClickHouse rejected with DECIMAL_OVERFLOW.
    var ingester = injector.getInstance(ChMetricsIngester.class);
    var driver = injector.getInstance(ChMetricsWalConsumerDriver.class);
    var promql = injector.getInstance(PromQlQueryProcessor.class);

    var resource = "svc-epoch-" + UUID.randomUUID();
    var metric = "epoch_gauge";
    var tags = Map.of("env", "dev", "test-session", testSession);

    // ts=500 ms is far below STALENESS_MS (300_000 ms); without the clamp,
    // startMs - STALENESS_MS = -299_500 would be sent to ClickHouse.
    ingester.ingestOtelProtobuf(
        buildGaugeRequest(resource, metric, tags, List.of(500L), List.of(9.0)));
    driver.onTick();

    var result = promql.queryRange(Constants.DEFAULT_TENANT, metric, 500L, 500L, 500L);
    assertNotNull(result);
    assertFalse(((InstantVectorResult) result).toMatrix().isEmpty());
  }

  @Test
  void promQlTsClientEscapesLabelKeysAndValues() throws Exception {
    var ingester = injector.getInstance(ChMetricsIngester.class);
    var driver = injector.getInstance(ChMetricsWalConsumerDriver.class);
    var clientFactory = injector.getInstance(TsClientFactory.class);

    var metric = "escaped_gauge";
    var tags =
        Map.of(
            "owner'group",
            "O'Reilly\\ops",
            "test-session",
            testSession);

    ingester.ingestOtelProtobuf(
        buildGaugeRequest("svc-escaped", metric, tags, List.of(1_000L), List.of(7.0)));
    driver.onTick();

    var scan =
        (GaugeScan)
            clientFactory
                .getClient(Constants.DEFAULT_TENANT)
                .orElseThrow()
                .get(metric, tags, RESOLUTION.SECONDLY, 1_000L, 1_000L);
    assertEquals(List.of(7.0f), scan.getValues());
  }

  @Test
  void queryRangeWithRangeFunctionProducesExpectedValues() throws Exception {
    var ingester = injector.getInstance(ChMetricsIngester.class);
    var driver = injector.getInstance(ChMetricsWalConsumerDriver.class);
    var promql = injector.getInstance(PromQlQueryProcessor.class);

    var resource = "svc-range-" + UUID.randomUUID();
    var metric = "mem_usage";
    var tags = Map.of("env", "dev", "test-session", testSession);

    ingester.ingestOtelProtobuf(
        buildGaugeRequest(resource, metric, tags, List.of(1_000L, 2_000L), List.of(2.0, 4.0)));
    driver.onTick();

    var result =
        promql.queryRange(
            Constants.DEFAULT_TENANT, "avg_over_time(mem_usage[2s])", 2_000L, 2_000L, 1_000L);
    assertNotNull(result);
    var matrix = ((InstantVectorResult) result).toMatrix();
    assertFalse(matrix.isEmpty());
    List<VectorData.Sample> samples = matrix.values().iterator().next();
    assertEquals(1, samples.size());
    assertEquals(2_000L, samples.get(0).ts());
    assertEquals(3.0f, samples.get(0).value());
  }

  @Test
  void labelNameValuesReturnsMetricNames() throws Exception {
    var ingester = injector.getInstance(ChMetricsIngester.class);
    var driver = injector.getInstance(ChMetricsWalConsumerDriver.class);
    var promql = injector.getInstance(PromQlQueryProcessor.class);

    var resource = "svc-labels-" + UUID.randomUUID();
    var metric = "disk_usage";
    var tags = Map.of("env", "prod", "test-session", testSession);

    ingester.ingestOtelProtobuf(
        buildGaugeRequest(resource, metric, tags, List.of(1_000L), List.of(5.0)));
    driver.onTick();

    var resp =
        promql.queryLabelsApi(
            Constants.DEFAULT_TENANT, "__name__", java.util.Collections.emptyList(), null, null);
    assertNotNull(resp);
    assertFalse(resp.getData().isEmpty());
    assertEquals(metric, resp.getData().getFirst());
  }

  private ExportMetricsServiceRequest buildGaugeRequest(
      String resourceName,
      String metricName,
      Map<String, String> tags,
      List<Long> ts,
      List<Double> vals) {
    var points = new ArrayList<NumberDataPoint>();
    for (int i = 0; i < ts.size(); i++) {
      var builder =
          NumberDataPoint.newBuilder()
              .setTimeUnixNano(ts.get(i) * 1_000_000)
              .setAsDouble(vals.get(i));
      for (var entry : tags.entrySet()) {
        builder.addAttributes(
            io.opentelemetry.proto.common.v1.KeyValue.newBuilder()
                .setKey(entry.getKey())
                .setValue(
                    io.opentelemetry.proto.common.v1.AnyValue.newBuilder()
                        .setStringValue(entry.getValue())
                        .build())
                .build());
      }
      points.add(builder.build());
    }
    var gauge = Gauge.newBuilder().addAllDataPoints(points).build();
    Metric metric = Metric.newBuilder().setName(metricName).setGauge(gauge).build();
    var scopeMetrics = ScopeMetrics.newBuilder().addMetrics(metric).build();
    var resource =
        Resource.newBuilder()
            .addAttributes(
                io.opentelemetry.proto.common.v1.KeyValue.newBuilder()
                    .setKey("service.name")
                    .setValue(
                        io.opentelemetry.proto.common.v1.AnyValue.newBuilder()
                            .setStringValue(resourceName)
                            .build())
                    .build())
            .build();
    var resourceMetrics =
        ResourceMetrics.newBuilder().setResource(resource).addScopeMetrics(scopeMetrics).build();
    return ExportMetricsServiceRequest.newBuilder().addResourceMetrics(resourceMetrics).build();
  }
}

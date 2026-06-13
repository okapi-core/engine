/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.metrics.ch;

import com.clickhouse.client.api.Client;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.okapi.ch.CreateChTablesSpec;
import org.okapi.metrics.pojos.AGG_TYPE;
import org.okapi.metrics.pojos.RES_TYPE;
import org.okapi.rest.metrics.query.GaugeQueryConfig;
import org.okapi.rest.metrics.query.GaugeSeries;
import org.okapi.rest.metrics.query.GetMetricsRequest;
import org.okapi.rest.metrics.query.METRIC_TYPE;
import org.okapi.testmodules.guice.TestChMetricsModule;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class ChGaugeTests {

  @TempDir Path tempDir;

  private Injector injector;
  private Client client;
  private final String testSession = java.util.UUID.randomUUID().toString();
  private final MetricsTestingOtelFactory otelFactory = new MetricsTestingOtelFactory(testSession);

  @BeforeEach
  void setup() {
    injector = Guice.createInjector(new TestChMetricsModule(tempDir.resolve("wal"), 16));
    client = injector.getInstance(Client.class);
    CreateChTablesSpec.migrate(client);
    truncateGaugeTable();
  }

  @Test
  void singleDatapointGaugeQuery() throws Exception {
    var ingester = injector.getInstance(ChMetricsIngester.class);
    var driver = injector.getInstance(ChMetricsWalConsumerDriver.class);
    var qp = injector.getInstance(ChMetricsQueryProcessor.class);

    ingester.ingestOtelProtobuf(
        otelFactory.buildGaugeRequest("svc-1", "metric_1", List.of(1_000L), List.of(1.0)));
    driver.onTick();

    var req =
        GetMetricsRequest.builder()
            .metric("metric_1")
            .tags(Map.of("env", "dev", "test-session", testSession))
            .start(0)
            .end(10_000)
            .metricType(METRIC_TYPE.GAUGE)
            .gaugeQueryConfig(new GaugeQueryConfig(RES_TYPE.SECONDLY, AGG_TYPE.AVG))
            .build();

    var resp = qp.getMetricsResponse(req);
    var gauge = resp.getGaugeResponse();
    assertNotNull(resp);
    assertEquals(List.of(1_000L), gauge.getSeries().get(0).getTimes());
    assertEquals(List.of(1.0f), gauge.getSeries().get(0).getValues());
    assertEquals("unit", resp.getGaugeResponse().getSeries().get(0).getUnit());
  }

  @Test
  void twoDatapointsGaugeQuery() throws Exception {
    var ingester = injector.getInstance(ChMetricsIngester.class);
    var driver = injector.getInstance(ChMetricsWalConsumerDriver.class);
    var qp = injector.getInstance(ChMetricsQueryProcessor.class);

    ingester.ingestOtelProtobuf(
        otelFactory.buildGaugeRequest(
            "svc-2", "metric_2", List.of(1_000L, 2_000L), List.of(1.0, 2.0)));
    driver.onTick();

    var req =
        GetMetricsRequest.builder()
            .metric("metric_2")
            .tags(Map.of("env", "dev", "test-session", testSession))
            .start(0)
            .end(10_000)
            .metricType(METRIC_TYPE.GAUGE)
            .gaugeQueryConfig(new GaugeQueryConfig(RES_TYPE.SECONDLY, AGG_TYPE.AVG))
            .build();

    var resp = qp.getMetricsResponse(req).getGaugeResponse();
    assertNotNull(resp);
    assertEquals(List.of(1_000L, 2_000L), resp.getSeries().get(0).getTimes());
    assertEquals(List.of(1.0f, 2.0f), resp.getSeries().get(0).getValues());
  }

  @Test
  void tagMismatchReturnsEmpty() throws Exception {
    var ingester = injector.getInstance(ChMetricsIngester.class);
    var driver = injector.getInstance(ChMetricsWalConsumerDriver.class);
    var qp = injector.getInstance(ChMetricsQueryProcessor.class);

    ingester.ingestOtelProtobuf(
        otelFactory.buildGaugeRequest("svc-3", "metric_3", List.of(1_000L), List.of(1.0)));
    driver.onTick();

    var req =
        GetMetricsRequest.builder()
            .metric("metric_3")
            .tags(Map.of("env", "prod", "test-session", testSession))
            .start(0)
            .end(10_000)
            .metricType(METRIC_TYPE.GAUGE)
            .gaugeQueryConfig(new GaugeQueryConfig(RES_TYPE.SECONDLY, AGG_TYPE.AVG))
            .build();

    var resp = qp.getMetricsResponse(req).getGaugeResponse();
    assertNull(resp);
  }

  @Test
  void timeWindowOutsideRangeReturnsEmpty() throws Exception {
    var ingester = injector.getInstance(ChMetricsIngester.class);
    var driver = injector.getInstance(ChMetricsWalConsumerDriver.class);
    var qp = injector.getInstance(ChMetricsQueryProcessor.class);

    ingester.ingestOtelProtobuf(
        otelFactory.buildGaugeRequest("svc-4", "metric_4", List.of(1_000L), List.of(1.0)));
    driver.onTick();

    var req =
        GetMetricsRequest.builder()
            .metric("metric_4")
            .tags(Map.of("env", "dev", "test-session", testSession))
            .start(5_000)
            .end(10_000)
            .metricType(METRIC_TYPE.GAUGE)
            .gaugeQueryConfig(new GaugeQueryConfig(RES_TYPE.SECONDLY, AGG_TYPE.AVG))
            .build();

    var resp = qp.getMetricsResponse(req).getGaugeResponse();
    assertNull(resp);
  }

  @Test
  void aggregationDoneOnlyByTagsAndName() throws Exception {
    var ingester = injector.getInstance(ChMetricsIngester.class);
    var driver = injector.getInstance(ChMetricsWalConsumerDriver.class);
    var qp = injector.getInstance(ChMetricsQueryProcessor.class);

    ingester.ingestOtelProtobuf(
        otelFactory.buildGaugeRequest("svc-keep", "metric_multi", List.of(1_000L), List.of(1.0)));
    ingester.ingestOtelProtobuf(
        otelFactory.buildGaugeRequest("svc-ignore", "metric_multi", List.of(1_000L), List.of(5.0)));
    driver.onTick();

    var req =
        GetMetricsRequest.builder()
            .metric("metric_multi")
            .tags(Map.of("env", "dev", "test-session", testSession))
            .start(0)
            .end(10_000)
            .metricType(METRIC_TYPE.GAUGE)
            .gaugeQueryConfig(new GaugeQueryConfig(RES_TYPE.SECONDLY, AGG_TYPE.AVG))
            .build();

    var resp = qp.getMetricsResponse(req).getGaugeResponse();
    assertNotNull(resp);
    assertEquals(List.of(1_000L), resp.getSeries().get(0).getTimes());
    assertEquals(List.of(3.0f), resp.getSeries().get(0).getValues());
  }

  @Test
  void sameBucketAggregationAvg() throws Exception {
    var ingester = injector.getInstance(ChMetricsIngester.class);
    var driver = injector.getInstance(ChMetricsWalConsumerDriver.class);
    var qp = injector.getInstance(ChMetricsQueryProcessor.class);

    ingester.ingestOtelProtobuf(
        otelFactory.buildGaugeRequest(
            "svc-5", "metric_same_bucket", List.of(1_000L, 1_050L), List.of(1.0, 3.0)));
    driver.onTick();

    var req =
        GetMetricsRequest.builder()
            .metric("metric_same_bucket")
            .tags(Map.of("env", "dev", "test-session", testSession))
            .start(0)
            .end(10_000)
            .metricType(METRIC_TYPE.GAUGE)
            .gaugeQueryConfig(new GaugeQueryConfig(RES_TYPE.SECONDLY, AGG_TYPE.AVG))
            .build();

    var resp = qp.getMetricsResponse(req).getGaugeResponse();
    assertNotNull(resp);
    assertEquals(List.of(1_000L), resp.getSeries().get(0).getTimes());
    assertEquals(List.of(2.0f), resp.getSeries().get(0).getValues());
  }

  @Test
  void minutelyResolutionSum() throws Exception {
    var ingester = injector.getInstance(ChMetricsIngester.class);
    var driver = injector.getInstance(ChMetricsWalConsumerDriver.class);
    var qp = injector.getInstance(ChMetricsQueryProcessor.class);

    ingester.ingestOtelProtobuf(
        otelFactory.buildGaugeRequest(
            "svc-6", "metric_minutely", List.of(1_000L, 30_000L), List.of(2.0, 5.0)));
    driver.onTick();

    var req =
        GetMetricsRequest.builder()
            .metric("metric_minutely")
            .tags(Map.of("env", "dev", "test-session", testSession))
            .start(0)
            .end(120_000)
            .metricType(METRIC_TYPE.GAUGE)
            .gaugeQueryConfig(new GaugeQueryConfig(RES_TYPE.MINUTELY, AGG_TYPE.SUM))
            .build();

    var resp = qp.getMetricsResponse(req).getGaugeResponse();
    assertNotNull(resp);
    assertEquals(1, resp.getSeries().get(0).getTimes().size());
    assertEquals(1, resp.getSeries().get(0).getValues().size());
    assertEquals(7.0f, resp.getSeries().get(0).getValues().getFirst());
  }

  @Test
  void queryMixedUnits() throws Exception {
    var ingester = injector.getInstance(ChMetricsIngester.class);
    var driver = injector.getInstance(ChMetricsWalConsumerDriver.class);
    var qp = injector.getInstance(ChMetricsQueryProcessor.class);

    ingester.ingestOtelProtobuf(
        otelFactory.buildGaugeRequest(
            "svc-7", "metric_units", List.of(1_000L), List.of(1.0), "ms"));
    ingester.ingestOtelProtobuf(
        otelFactory.buildGaugeRequest(
            "svc-8", "metric_units", List.of(1_000L), List.of(2.0), "s"));
    driver.onTick();

    var req =
        GetMetricsRequest.builder()
            .metric("metric_units")
            .tags(Map.of("env", "dev", "test-session", testSession))
            .start(0)
            .end(10_000)
            .metricType(METRIC_TYPE.GAUGE)
            .gaugeQueryConfig(new GaugeQueryConfig(RES_TYPE.SECONDLY, AGG_TYPE.AVG))
            .build();

    var resp = qp.getMetricsResponse(req);
    var gauge = resp.getGaugeResponse();
    assertNotNull(gauge);
    var expected =
        List.of(
            GaugeSeries.builder()
                .unit("ms")
                .tags(Map.of("env", "dev", "test-session", testSession))
                .times(List.of(1_000L))
                .values(List.of(1.0f))
                .build(),
            GaugeSeries.builder()
                .unit("s")
                .tags(Map.of("env", "dev", "test-session", testSession))
                .times(List.of(1_000L))
                .values(List.of(2.0f))
                .build());
    assertEquals(expected, gauge.getSeries());
  }

  @Test
  void gaugeWithNoUnit_returnsEmptyUnit() throws Exception {
    var ingester = injector.getInstance(ChMetricsIngester.class);
    var driver = injector.getInstance(ChMetricsWalConsumerDriver.class);
    var qp = injector.getInstance(ChMetricsQueryProcessor.class);

    ingester.ingestOtelProtobuf(
        otelFactory.buildGaugeRequest(
            "svc-nounit", "metric_nounit", List.of(1_000L), List.of(1.0), ""));
    driver.onTick();

    var req =
        GetMetricsRequest.builder()
            .metric("metric_nounit")
            .tags(Map.of("env", "dev", "test-session", testSession))
            .start(0)
            .end(10_000)
            .metricType(METRIC_TYPE.GAUGE)
            .gaugeQueryConfig(new GaugeQueryConfig(RES_TYPE.SECONDLY, AGG_TYPE.AVG))
            .build();

    var series = qp.getMetricsResponse(req).getGaugeResponse().getSeries();
    assertEquals(1, series.size());
    assertEquals("", series.get(0).getUnit());
  }

  private void truncateGaugeTable() {
    client.queryAll("TRUNCATE TABLE IF EXISTS okapi_metrics.gauge_raw_samples");
  }
}

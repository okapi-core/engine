/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.metrics.ch;

import static org.junit.jupiter.api.Assertions.*;

import com.clickhouse.client.api.Client;
import com.google.inject.Guice;
import com.google.inject.Injector;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.metrics.v1.*;
import io.opentelemetry.proto.resource.v1.Resource;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.okapi.ch.CreateChTablesSpec;
import org.okapi.chtest.ChTestOnlyUtils;
import org.okapi.rest.metrics.query.GetMetricsRequest;
import org.okapi.rest.metrics.query.GetSumsQueryConfig;
import org.okapi.rest.metrics.query.METRIC_TYPE;
import org.okapi.testmodules.guice.TestChMetricsModule;

public class ChSumTests {
  @TempDir java.nio.file.Path tempDir;

  private Injector injector;
  private Client client;
  private String testSession;

  @BeforeEach
  void setup() {
    testSession = UUID.randomUUID().toString();
    injector = Guice.createInjector(new TestChMetricsModule(tempDir.resolve("wal"), 16));
    client = injector.getInstance(Client.class);
    CreateChTablesSpec.migrate(client);
    ChTestOnlyUtils.truncateTable(client, ChConstants.TBL_SUM);
    ChTestOnlyUtils.truncateTable(client, ChConstants.TBL_METRIC_EVENTS_META);
  }

  @Test
  void deltaAggregateSumsTwoPoints() throws Exception {
    var ingester = injector.getInstance(ChMetricsIngester.class);
    var driver = injector.getInstance(ChMetricsWalConsumerDriver.class);
    var qp = injector.getInstance(ChMetricsQueryProcessor.class);

    var resource = "svc-sum-" + UUID.randomUUID();
    var tags = Map.of("env", "dev", "test-session", testSession);

    var metricWithUnit = "metric_sum_unit";
    var metricWithoutUnit = "metric_sum_no_unit";

    ingester.ingestOtelProtobuf(
        buildSumRequest(
            resource,
            metricWithUnit,
            tags,
            "ms",
            AggregationTemporality.AGGREGATION_TEMPORALITY_DELTA,
            List.of(numberPoint(1_000L, 2_000L, 3.0), numberPoint(2_000L, 3_000L, 4.0))));
    ingester.ingestOtelProtobuf(
        buildSumRequest(
            resource,
            metricWithoutUnit,
            tags,
            null,
            AggregationTemporality.AGGREGATION_TEMPORALITY_DELTA,
            List.of(numberPoint(1_000L, 2_000L, 3.0), numberPoint(2_000L, 3_000L, 4.0))));
    driver.onTick();

    var queryReq =
        GetMetricsRequest.builder()
            .metric(metricWithUnit)
            .tags(tags)
            .start(0)
            .end(5_000)
            .metricType(METRIC_TYPE.SUM)
            .sumsQueryConfig(
                GetSumsQueryConfig.builder()
                    .temporality(GetSumsQueryConfig.TEMPORALITY.DELTA_AGGREGATE)
                    .build())
            .build();

    var resp = qp.getMetricsResponse(queryReq);
    assertNotNull(resp.getSumsResponse());
    var sums = resp.getSumsResponse().getSums();
    assertEquals(1, sums.size());
    assertEquals(7L, sums.get(0).getCount());
    assertEquals("ms", sums.get(0).getUnit());

    var noUnitResp = qp.getMetricsResponse(queryReq.toBuilder().metric(metricWithoutUnit).build());
    assertNotNull(noUnitResp.getSumsResponse());
    var noUnitSums = noUnitResp.getSumsResponse().getSums();
    assertEquals(1, noUnitSums.size());
    assertEquals(7L, noUnitSums.get(0).getCount());
    assertEquals("", noUnitSums.get(0).getUnit());

    var cumulativeReq =
        GetMetricsRequest.builder()
            .metric(metricWithUnit)
            .tags(tags)
            .start(0)
            .end(5_000)
            .metricType(METRIC_TYPE.SUM)
            .sumsQueryConfig(
                GetSumsQueryConfig.builder()
                    .temporality(GetSumsQueryConfig.TEMPORALITY.CUMULATIVE)
                    .build())
            .build();
    assertNull(qp.getMetricsResponse(cumulativeReq).getSumsResponse());
    assertNull(
        qp.getMetricsResponse(cumulativeReq.toBuilder().metric(metricWithoutUnit).build())
            .getSumsResponse());
  }

  @Test
  void cumulativeReturnsLatestValueAndDeltaAggregateReturnsNull() throws Exception {
    var ingester = injector.getInstance(ChMetricsIngester.class);
    var driver = injector.getInstance(ChMetricsWalConsumerDriver.class);
    var qp = injector.getInstance(ChMetricsQueryProcessor.class);

    var resource = "svc-sum-" + UUID.randomUUID();
    var tags = Map.of("env", "dev", "test-session", testSession);

    var metricWithUnit = "metric_sum_cumulative_unit";
    var metricWithoutUnit = "metric_sum_cumulative_no_unit";

    ingester.ingestOtelProtobuf(
        buildSumRequest(
            resource,
            metricWithUnit,
            tags,
            "ms",
            AggregationTemporality.AGGREGATION_TEMPORALITY_CUMULATIVE,
            List.of(
                numberPoint(1_000L, 2_000L, 10.0),
                numberPoint(2_000L, 3_000L, 15.0),
                numberPoint(3_000L, 4_000L, 19.0))));
    ingester.ingestOtelProtobuf(
        buildSumRequest(
            resource,
            metricWithoutUnit,
            tags,
            null,
            AggregationTemporality.AGGREGATION_TEMPORALITY_CUMULATIVE,
            List.of(
                numberPoint(1_000L, 2_000L, 10.0),
                numberPoint(2_000L, 3_000L, 15.0),
                numberPoint(3_000L, 4_000L, 19.0))));
    driver.onTick();

    var cumulativeReq =
        GetMetricsRequest.builder()
            .metric(metricWithUnit)
            .tags(tags)
            .start(0)
            .end(5_000)
            .metricType(METRIC_TYPE.SUM)
            .sumsQueryConfig(
                GetSumsQueryConfig.builder()
                    .temporality(GetSumsQueryConfig.TEMPORALITY.CUMULATIVE)
                    .build())
            .build();
    var cumulativeResp = qp.getMetricsResponse(cumulativeReq);
    assertNotNull(cumulativeResp.getSumsResponse());
    var sums = cumulativeResp.getSumsResponse().getSums();
    assertEquals(1, sums.size());
    assertEquals(19L, sums.get(0).getCount());
    assertEquals("ms", sums.get(0).getUnit());

    var noUnitResp =
        qp.getMetricsResponse(cumulativeReq.toBuilder().metric(metricWithoutUnit).build());
    assertNotNull(noUnitResp.getSumsResponse());
    var noUnitSums = noUnitResp.getSumsResponse().getSums();
    assertEquals(1, noUnitSums.size());
    assertEquals(19L, noUnitSums.get(0).getCount());
    assertEquals("", noUnitSums.get(0).getUnit());

    var deltaReq =
        GetMetricsRequest.builder()
            .metric(metricWithUnit)
            .tags(tags)
            .start(0)
            .end(5_000)
            .metricType(METRIC_TYPE.SUM)
            .sumsQueryConfig(
                GetSumsQueryConfig.builder()
                    .temporality(GetSumsQueryConfig.TEMPORALITY.DELTA_AGGREGATE)
                    .build())
            .build();
    assertNull(qp.getMetricsResponse(deltaReq).getSumsResponse());
    assertNull(
        qp.getMetricsResponse(deltaReq.toBuilder().metric(metricWithoutUnit).build())
            .getSumsResponse());
  }

  @Test
  void mixedUnitQueriesForDeltaAndCumulative() throws Exception {
    var ingester = injector.getInstance(ChMetricsIngester.class);
    var driver = injector.getInstance(ChMetricsWalConsumerDriver.class);
    var qp = injector.getInstance(ChMetricsQueryProcessor.class);

    var resource = "svc-sum-mixed-" + UUID.randomUUID();
    var tags = Map.of("env", "dev", "test-session", testSession);

    var deltaMetric = "metric_sum_mixed_delta";
    ingester.ingestOtelProtobuf(
        buildSumRequest(
            resource,
            deltaMetric,
            tags,
            "ms",
            AggregationTemporality.AGGREGATION_TEMPORALITY_DELTA,
            List.of(numberPoint(1_000L, 2_000L, 3.0), numberPoint(2_000L, 3_000L, 4.0))));
    ingester.ingestOtelProtobuf(
        buildSumRequest(
            resource,
            deltaMetric,
            tags,
            null,
            AggregationTemporality.AGGREGATION_TEMPORALITY_DELTA,
            List.of(numberPoint(1_000L, 2_000L, 5.0))));

    var cumulativeMetric = "metric_sum_mixed_cumulative";
    ingester.ingestOtelProtobuf(
        buildSumRequest(
            resource,
            cumulativeMetric,
            tags,
            "ms",
            AggregationTemporality.AGGREGATION_TEMPORALITY_CUMULATIVE,
            List.of(numberPoint(1_000L, 2_000L, 10.0), numberPoint(2_000L, 3_000L, 12.0))));
    ingester.ingestOtelProtobuf(
        buildSumRequest(
            resource,
            cumulativeMetric,
            tags,
            null,
            AggregationTemporality.AGGREGATION_TEMPORALITY_CUMULATIVE,
            List.of(numberPoint(1_000L, 2_000L, 7.0), numberPoint(2_000L, 3_000L, 9.0))));

    driver.onTick();

    var deltaReq =
        GetMetricsRequest.builder()
            .metric(deltaMetric)
            .tags(tags)
            .start(0)
            .end(5_000)
            .metricType(METRIC_TYPE.SUM)
            .sumsQueryConfig(
                GetSumsQueryConfig.builder()
                    .temporality(GetSumsQueryConfig.TEMPORALITY.DELTA_AGGREGATE)
                    .build())
            .build();
    var deltaResp = qp.getMetricsResponse(deltaReq);
    assertNotNull(deltaResp.getSumsResponse());
    var deltaByUnit =
        deltaResp.getSumsResponse().getSums().stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    org.okapi.rest.metrics.query.Sum::getUnit, sum -> sum));
    assertEquals(2, deltaByUnit.size());
    assertEquals(7L, deltaByUnit.get("ms").getCount());
    assertEquals(5L, deltaByUnit.get("").getCount());

    var cumulativeReq =
        GetMetricsRequest.builder()
            .metric(cumulativeMetric)
            .tags(tags)
            .start(0)
            .end(5_000)
            .metricType(METRIC_TYPE.SUM)
            .sumsQueryConfig(
                GetSumsQueryConfig.builder()
                    .temporality(GetSumsQueryConfig.TEMPORALITY.CUMULATIVE)
                    .build())
            .build();
    var cumulativeResp = qp.getMetricsResponse(cumulativeReq);
    assertNotNull(cumulativeResp.getSumsResponse());
    var cumulativeByUnit =
        cumulativeResp.getSumsResponse().getSums().stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    org.okapi.rest.metrics.query.Sum::getUnit, sum -> sum));
    assertEquals(2, cumulativeByUnit.size());
    assertEquals(12L, cumulativeByUnit.get("ms").getCount());
    assertEquals(9L, cumulativeByUnit.get("").getCount());
  }

  private ExportMetricsServiceRequest buildSumRequest(
      String resourceName,
      String metricName,
      Map<String, String> tags,
      String unit,
      AggregationTemporality temporality,
      List<NumberDataPoint> points) {
    var sum =
        Sum.newBuilder()
            .setAggregationTemporality(temporality)
            .setIsMonotonic(false)
            .addAllDataPoints(points)
            .build();
    var metricBuilder = Metric.newBuilder().setName(metricName).setSum(sum);
    if (unit != null) {
      metricBuilder.setUnit(unit);
    }
    var metric = metricBuilder.build();
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

  private NumberDataPoint numberPoint(long startMs, long endMs, double val) {
    var builder =
        NumberDataPoint.newBuilder()
            .setStartTimeUnixNano(startMs * 1_000_000)
            .setTimeUnixNano(endMs * 1_000_000)
            .setAsDouble(val);
    builder.addAttributes(
        io.opentelemetry.proto.common.v1.KeyValue.newBuilder()
            .setKey("env")
            .setValue(
                io.opentelemetry.proto.common.v1.AnyValue.newBuilder()
                    .setStringValue("dev")
                    .build())
            .build());
    builder.addAttributes(
        io.opentelemetry.proto.common.v1.KeyValue.newBuilder()
            .setKey("test-session")
            .setValue(
                io.opentelemetry.proto.common.v1.AnyValue.newBuilder()
                    .setStringValue(testSession)
                    .build())
            .build());
    return builder.build();
  }
}

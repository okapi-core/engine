/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.metrics.ch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.clickhouse.client.api.Client;
import com.google.inject.Guice;
import com.google.inject.Injector;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.DoubleStream;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.okapi.ch.CreateChTablesSpec;
import org.okapi.chtest.ChTestOnlyUtils;
import org.okapi.rest.common.KeyValueJson;
import org.okapi.rest.metrics.Exemplar;
import org.okapi.rest.metrics.exemplar.GetExemplarsRequest;
import org.okapi.rest.traces.TimestampFilter;
import org.okapi.testmodules.guice.TestChMetricsModule;
import org.okapi.timeutils.TimeUtils;

public class ChExemplarQueryProcessorTests {

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
    ChTestOnlyUtils.truncateTable(client, ChConstants.TBL_EXEMPLAR);
  }

  @Test
  void querySinglePoint() throws Exception {
    var ingester = injector.getInstance(ChMetricsIngester.class);
    var driver = injector.getInstance(ChMetricsWalConsumerDriver.class);
    var qp = injector.getInstance(ChMetricsQueryProcessor.class);

    var metric = "metric.exemplar.single";
    var tags = tagsWithSession("us-east");
    var spanId = "0000000000000001";
    var traceId = "00000000000000000000000000000001";
    ingester.ingestOtelProtobuf(
        otelFactory.buildGaugeWithExemplarData(
            metric, tags, List.of(1_000L), List.of(1.25), spanId, traceId));
    driver.onTick();

    var resp = qp.getExemplars(buildRequest(metric, tags, 0, 2_000));
    assertNotNull(resp);
    assertEquals(metric, resp.getMetric());
    assertEquals(tags, resp.getLabels());
    assertEquals(1, resp.getExemplars().size());

    var exemplar = resp.getExemplars().get(0);
    assertEquals(TimeUtils.millisToNanos(1_000L), exemplar.getTsNanos());
    assertEquals(spanId, exemplar.getSpanId());
    assertEquals(traceId, exemplar.getTraceId());
    assertNotNull(exemplar.getMeasurement());
    assertEquals(1.25, exemplar.getMeasurement().getADouble(), 0.0001);

    var kvMap = kvToMap(exemplar.getKv());
    assertEquals("0", kvMap.get("exemplar.index"));
    assertEquals("unit-test", kvMap.get("exemplar.source"));
  }

  @Test
  void queryMatchingFilters() throws Exception {
    var ingester = injector.getInstance(ChMetricsIngester.class);
    var driver = injector.getInstance(ChMetricsWalConsumerDriver.class);
    var qp = injector.getInstance(ChMetricsQueryProcessor.class);

    var metric = "metric.exemplar.filters";
    var tagsEast = tagsWithSession("us-east");
    var tagsWest = tagsWithSession("us-west");
    ingester.ingestOtelProtobuf(
        otelFactory.buildGaugeWithExemplarData(
            metric,
            tagsEast,
            List.of(1_000L, 2_000L, 3_000L),
            List.of(1.0, 2.0, 3.0),
            "0000000000000002",
            "00000000000000000000000000000002"));
    ingester.ingestOtelProtobuf(
        otelFactory.buildGaugeWithExemplarData(
            metric,
            tagsWest,
            List.of(2_000L),
            List.of(9.0),
            "0000000000000003",
            "00000000000000000000000000000003"));
    driver.onTick();

    var resp = qp.getExemplars(buildRequest(metric, Map.of("region", "us-east"), 1_500, 2_500));
    assertNotNull(resp);
    assertEquals(1, resp.getExemplars().size());
    assertEquals(TimeUtils.millisToNanos(2_000L), resp.getExemplars().get(0).getTsNanos());
  }

  @Test
  void queryLabelMismatch() throws Exception {
    var ingester = injector.getInstance(ChMetricsIngester.class);
    var driver = injector.getInstance(ChMetricsWalConsumerDriver.class);
    var qp = injector.getInstance(ChMetricsQueryProcessor.class);

    var metric = "metric.exemplar.labels";
    var tags = tagsWithSession("us-east");
    ingester.ingestOtelProtobuf(
        otelFactory.buildGaugeWithExemplarData(
            metric,
            tags,
            List.of(1_000L),
            List.of(1.0),
            "0000000000000004",
            "00000000000000000000000000000004"));
    driver.onTick();

    var mismatchTags = tagsWithSession("us-west");
    var resp = qp.getExemplars(buildRequest(metric, mismatchTags, 0, 2_000));
    assertNotNull(resp);
    assertEquals(0, resp.getExemplars().size());
  }

  @Test
  void queryNameMismatch() throws Exception {
    var ingester = injector.getInstance(ChMetricsIngester.class);
    var driver = injector.getInstance(ChMetricsWalConsumerDriver.class);
    var qp = injector.getInstance(ChMetricsQueryProcessor.class);

    var metric = "metric.exemplar.expected";
    var tags = tagsWithSession("us-east");
    ingester.ingestOtelProtobuf(
        otelFactory.buildGaugeWithExemplarData(
            metric,
            tags,
            List.of(1_000L),
            List.of(1.0),
            "0000000000000005",
            "00000000000000000000000000000005"));
    driver.onTick();

    var resp = qp.getExemplars(buildRequest("metric.exemplar.other", tags, 0, 2_000));
    assertNotNull(resp);
    assertEquals(0, resp.getExemplars().size());
  }

  @Test
  void queryMultipleMatch() throws Exception {
    var ingester = injector.getInstance(ChMetricsIngester.class);
    var driver = injector.getInstance(ChMetricsWalConsumerDriver.class);
    var qp = injector.getInstance(ChMetricsQueryProcessor.class);

    var metric = "metric.exemplar.multi";
    var tags = tagsWithSession("us-east");
    ingester.ingestOtelProtobuf(
        otelFactory.buildGaugeWithExemplarData(
            metric,
            tags,
            List.of(1_000L, 2_000L, 2_500L),
            List.of(1.0, 2.0, 3.0),
            "0000000000000006",
            "00000000000000000000000000000006"));
    driver.onTick();

    var resp = qp.getExemplars(buildRequest(metric, tags, 500, 3_000));
    assertNotNull(resp);
    assertEquals(3, resp.getExemplars().size());

    var ts = resp.getExemplars().stream().map(Exemplar::getTsNanos).sorted().toList();
    assertEquals(
        List.of(
            TimeUtils.millisToNanos(1_000L),
            TimeUtils.millisToNanos(2_000L),
            TimeUtils.millisToNanos(2_500L)),
        ts);

    var indices =
        resp.getExemplars().stream()
            .map(exemplar -> kvToMap(exemplar.getKv()).get("exemplar.index"))
            .sorted()
            .toList();
    assertEquals(List.of("0", "1", "2"), indices);
  }

  @Test
  void queryCapsResultsAtOneThousandExemplars() throws Exception {
    var ingester = injector.getInstance(ChMetricsIngester.class);
    var driver = injector.getInstance(ChMetricsWalConsumerDriver.class);
    var qp = injector.getInstance(ChMetricsQueryProcessor.class);

    var metric = "metric.exemplar.limit";
    var tags = tagsWithSession("us-east");
    var timestamps = LongStream.rangeClosed(1, 1_001).boxed().toList();
    var values = DoubleStream.iterate(1.0, value -> value + 1.0).limit(1_001).boxed().toList();

    ingester.ingestOtelProtobuf(
        otelFactory.buildGaugeWithExemplarData(
            metric,
            tags,
            timestamps,
            values,
            "0000000000000007",
            "00000000000000000000000000000007"));
    driver.onTick();

    var resp = qp.getExemplars(buildRequest(metric, Map.of("region", "us-east"), 0, 2_000));

    assertEquals(1_000, resp.getExemplars().size());
  }

  @Test
  void queryExactMatchWithCompleteLabels() throws Exception {
    var metric = "metric.exemplar.explicit.exact";
    var tags = tagsWithSession("us-east");
    var qp = ingestSeries(metric, tags, "0000000000000008", "00000000000000000000000000000008");

    var resp = qp.getExemplars(buildRequest(metric, tags, 0, 2_000));

    assertEquals(1, resp.getExemplars().size());
    assertEquals(tags, resp.getExemplars().get(0).getTags());
  }

  @Test
  void querySubsetMatchWithOmittedLabels() throws Exception {
    var metric = "metric.exemplar.explicit.subset";
    var tagsEast = tagsWithSession("us-east");
    var tagsWest = tagsWithSession("us-west");
    var qp = ingestSeries(metric, tagsEast, "0000000000000009", "00000000000000000000000000000009");
    ingestSeries(metric, tagsWest, "000000000000000a", "0000000000000000000000000000000a");

    var resp = qp.getExemplars(buildRequest(metric, Map.of("env", "dev"), 0, 2_000));

    assertEquals(2, resp.getExemplars().size());
  }

  private ChMetricsQueryProcessor ingestSeries(
      String metric, Map<String, String> tags, String spanId, String traceId) throws Exception {
    var ingester = injector.getInstance(ChMetricsIngester.class);
    var driver = injector.getInstance(ChMetricsWalConsumerDriver.class);
    ingester.ingestOtelProtobuf(
        otelFactory.buildGaugeWithExemplarData(
            metric, tags, List.of(1_000L), List.of(1.0), spanId, traceId));
    driver.onTick();
    return injector.getInstance(ChMetricsQueryProcessor.class);
  }

  private GetExemplarsRequest buildRequest(
      String metric, Map<String, String> labels, long startMs, long endMs) {
    var timeFilter =
        TimestampFilter.builder()
            .tsStartNanos(TimeUtils.millisToNanos(startMs))
            .tsEndNanos(TimeUtils.millisToNanos(endMs))
            .build();
    return GetExemplarsRequest.builder()
        .metric(metric)
        .labels(labels)
        .timeFilter(timeFilter)
        .build();
  }

  private Map<String, String> tagsWithSession(String region) {
    var tags = new LinkedHashMap<String, String>();
    tags.put("env", "dev");
    tags.put("region", region);
    tags.put("test-session", testSession);
    return tags;
  }

  private static Map<String, String> kvToMap(List<KeyValueJson> kvs) {
    var out = new LinkedHashMap<String, String>();
    for (var kv : kvs) {
      var value = kv.getValue();
      out.put(kv.getKey(), value == null ? null : value.getAString());
    }
    return out;
  }
}

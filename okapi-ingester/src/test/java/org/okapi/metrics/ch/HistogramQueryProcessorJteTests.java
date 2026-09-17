/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.metrics.ch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.clickhouse.client.api.Client;
import com.google.gson.Gson;
import com.google.inject.Guice;
import com.google.inject.Injector;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.okapi.ch.CreateChTablesSpec;
import org.okapi.metrics.ch.template.ChMetricTemplateEngine;
import org.okapi.rest.metrics.query.GetMetricsRequest;
import org.okapi.rest.metrics.query.HistoQueryConfig;
import org.okapi.rest.metrics.query.METRIC_TYPE;
import org.okapi.testmodules.guice.TestChMetricsModule;

/** Sanity test for the JTE-based histogram query execution path. */
public class HistogramQueryProcessorJteTests {
  @TempDir java.nio.file.Path tempDir;

  private Injector injector;
  private Client client;
  private final Gson gson = new Gson();

  @BeforeEach
  void setup() {
    injector = Guice.createInjector(new TestChMetricsModule(tempDir.resolve("wal"), 16));
    client = injector.getInstance(Client.class);
    client.queryAll("DROP TABLE IF EXISTS okapi_metrics.histo_raw_samples");
    CreateChTablesSpec.migrate(client);
    client.queryAll("TRUNCATE TABLE IF EXISTS " + ChConstants.TBL_HISTOS);
  }

  @Test
  void fetchesSingleDeltaHistogram() throws Exception {
    var writer = injector.getInstance(ChWriter.class);
    var templateEngine = injector.getInstance(ChMetricTemplateEngine.class);
    var processor = new HistogramQueryProcessor(client, templateEngine);

    var resource = "svc-histo-" + UUID.randomUUID();
    var metric = "metric_h";
    var tags = Map.of("env", "dev");

    var buckets = new float[] {10.0f, 20.0f};
    var counts = new long[] {1, 2, 3};

    var row =
        ChHistoSample.builder()
            .metric(metric)
            .tags(tags)
            .tsStart(1_000)
            .tsEnd(2_000)
            .buckets(buckets)
            .counts(counts)
            .histoType(ChHistoSample.HISTO_TYPE.DELTA)
            .build();
    writer.writeHistoSamplesBinary(List.of(row)).get();

    var req =
        GetMetricsRequest.builder()
            .metric(metric)
            .tags(tags)
            .start(0)
            .end(5_000)
            .metricType(METRIC_TYPE.HISTO)
            .histoQueryConfig(
                HistoQueryConfig.builder().temporality(HistoQueryConfig.TEMPORALITY.DELTA).build())
            .build();

    var resp = processor.getHistoRes(req);
    assertNotNull(resp.getHistogramResponse());
    var series = resp.getHistogramResponse().getSeries();
    assertNotNull(series);
    assertEquals(1, series.size());
    assertEquals(List.of(1L, 2L, 3L), series.getFirst().getHistogram().getCounts());
    assertEquals(List.of(10.0f, 20.0f), series.getFirst().getHistogram().getBuckets());
  }

  @Test
  void fetchesMultipleSeriesForSubsetTags() throws Exception {
    var writer = injector.getInstance(ChWriter.class);
    var templateEngine = injector.getInstance(ChMetricTemplateEngine.class);
    var processor = new HistogramQueryProcessor(client, templateEngine);

    var metric = "metric_h_multi";
    var tagsA = Map.of("env", "dev", "host", "a");
    var tagsB = Map.of("env", "dev", "host", "b");

    var buckets = new float[] {10.0f, 20.0f};
    var counts = new long[] {1, 2, 3};

    var rowA =
        ChHistoSample.builder()
            .metric(metric)
            .tags(tagsA)
            .tsStart(1_000)
            .tsEnd(2_000)
            .buckets(buckets)
            .counts(counts)
            .histoType(ChHistoSample.HISTO_TYPE.DELTA)
            .build();
    var rowB =
        ChHistoSample.builder()
            .metric(metric)
            .tags(tagsB)
            .tsStart(1_000)
            .tsEnd(2_000)
            .buckets(buckets)
            .counts(counts)
            .histoType(ChHistoSample.HISTO_TYPE.DELTA)
            .build();
    writer.writeHistoSamplesBinary(List.of(rowA, rowB)).get();

    var req =
        GetMetricsRequest.builder()
            .metric(metric)
            .tags(Map.of("env", "dev"))
            .start(0)
            .end(5_000)
            .metricType(METRIC_TYPE.HISTO)
            .histoQueryConfig(
                HistoQueryConfig.builder().temporality(HistoQueryConfig.TEMPORALITY.DELTA).build())
            .build();

    var resp = processor.getHistoRes(req);
    assertNotNull(resp.getHistogramResponse());
    var series = resp.getHistogramResponse().getSeries();
    assertNotNull(series);
    assertEquals(2, series.size());
    var tags = series.stream().map(s -> s.getTags()).collect(Collectors.toSet());
    assertEquals(Set.of(tagsA, tagsB), tags);
  }

  private static String formatTs(long epochMillis) {
    return Instant.ofEpochMilli(epochMillis)
        .atOffset(ZoneOffset.UTC)
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
  }
}

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
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.okapi.ch.CreateChTablesSpec;
import org.okapi.rest.metrics.query.GetMetricsRequest;
import org.okapi.rest.metrics.query.GetSumsQueryConfig;
import org.okapi.rest.metrics.query.METRIC_TYPE;
import org.okapi.testmodules.guice.TestChMetricsModule;

/** Basic sanity check for SumQueryProcessor using the JTE-built query. */
public class SumQueryProcessorJteTests {
  @TempDir Path tempDir;

  private Injector injector;
  private Client client;
  private final Gson gson = new Gson();

  @BeforeEach
  void setup() throws IOException {
    injector = Guice.createInjector(new TestChMetricsModule(tempDir.resolve("wal"), 16));
    client = injector.getInstance(Client.class);
    CreateChTablesSpec.migrate(client);
    client.queryAll("TRUNCATE TABLE IF EXISTS " + ChConstants.TBL_SUM);
  }

  @Test
  void deltaAggregateSumsTwoPoints() throws Exception {
    var writer = injector.getInstance(ChWriter.class);
    var sumProcessor = injector.getInstance(SumQueryProcessor.class);

    var resource = "svc-sum-" + UUID.randomUUID();
    var metric = "sum_metric";
    var tags = Map.of("env", "dev");

    var row1 =
        gson.toJson(
            Map.of(
                "metric_name",
                metric,
                "tags",
                tags,
                "ts_start",
                formatTs(1_000),
                "ts_end",
                formatTs(2_000),
                "value",
                3,
                "histo_type",
                "DELTA"));
    var row2 =
        gson.toJson(
            Map.of(
                "metric_name",
                metric,
                "tags",
                tags,
                "ts_start",
                formatTs(2_000),
                "ts_end",
                formatTs(3_000),
                "value",
                4,
                "histo_type",
                "DELTA"));

    writer.writeRows(ChConstants.TBL_SUM, List.of(row1, row2)).get();

    var req =
        GetMetricsRequest.builder()
            .metric(metric)
            .tags(tags)
            .start(0)
            .end(5_000)
            .metricType(METRIC_TYPE.SUM)
            .sumsQueryConfig(
                GetSumsQueryConfig.builder()
                    .temporality(GetSumsQueryConfig.TEMPORALITY.DELTA_AGGREGATE)
                    .build())
            .build();

    var resp = sumProcessor.getSumRes(req);
    assertNotNull(resp.getSumsResponse());
    var sums = resp.getSumsResponse().getSums();
    assertNotNull(sums);
    assertEquals(1, sums.size());
    assertEquals(7L, sums.get(0).getCount());
  }

  private static String formatTs(long epochMillis) {
    return Instant.ofEpochMilli(epochMillis)
        .atOffset(ZoneOffset.UTC)
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
  }
}

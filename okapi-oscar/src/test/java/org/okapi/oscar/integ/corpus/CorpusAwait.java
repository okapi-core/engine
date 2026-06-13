package org.okapi.oscar.integ.corpus;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.enums.Protocol;
import org.awaitility.Awaitility;
import org.okapi.ingester.client.IngesterClient;
import org.okapi.rest.search.SearchMetricsRequest;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class CorpusAwait {

  private static final List<String> TABLES =
      List.of(
          "okapi_metrics.gauge_raw_samples",
          "okapi_metrics.histo_raw_samples",
          "okapi_metrics.sums_raw_samples",
          "okapi_metrics.metric_exemplars",
          "okapi_metrics.metric_events_stream_meta",
          "okapi_traces.service_red_events",
          "okapi_traces.spans_table_v1",
          "okapi_traces.spans_ingested_attribs");

  private static final Client CH_CLIENT =
      new Client.Builder()
          .addEndpoint(
              Protocol.HTTP,
              System.getenv().getOrDefault("OKAPI_TEST_CLICKHOUSE_HOST", "127.0.0.1"),
              Integer.parseInt(
                  System.getenv().getOrDefault("OKAPI_TEST_CLICKHOUSE_PORT", "8123")),
              false)
          .setUsername("default")
          .setPassword("okapi_testing_password")
          .build();

  public static void truncateAll() {
    for (var table : TABLES) {
      var statement = "TRUNCATE TABLE IF EXISTS " + table;
      CH_CLIENT.execute(statement);
    }
  }

  public static void awaitMetric(IngesterClient ingesterClient, String metricName) {
    Awaitility.await()
        .atMost(30, TimeUnit.SECONDS)
        .pollInterval(500, TimeUnit.MILLISECONDS)
        .until(
            () -> {
              var resp =
                  ingesterClient.searchMetrics(
                      SearchMetricsRequest.builder()
                          .tsStartMillis(0)
                          .tsEndMillis(System.currentTimeMillis())
                          .metricName(metricName)
                          .build());
              return resp.getMatchingPaths() != null && !resp.getMatchingPaths().isEmpty();
            });
  }
}

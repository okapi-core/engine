/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.chtest;

import com.clickhouse.client.api.Client;

public class ChTestOnlyUtils {
  public static void truncateTable(Client client, String table) {
    var targetTable =
        switch (table) {
          case "okapi_metrics.gauge_raw_samples" -> "okapi_metrics.gauge_raw_samples_local";
          case "okapi_metrics.histo_raw_samples" -> "okapi_metrics.histo_raw_samples_local";
          case "okapi_metrics.exponential_histo_raw_samples" ->
              "okapi_metrics.exponential_histo_raw_samples_local";
          case "okapi_metrics.sums_raw_samples" -> "okapi_metrics.sums_raw_samples_local";
          case "okapi_metrics.metric_exemplars" -> "okapi_metrics.metric_exemplars_local";
          case "okapi_metrics.metric_events_stream_meta" ->
              "okapi_metrics.metric_events_stream_meta_local";
          default -> table;
        };
    var truncateTable =
        "TRUNCATE TABLE IF EXISTS "
            + targetTable
            + " ON CLUSTER 'okapi' SETTINGS distributed_ddl_task_timeout = 10";
    client.queryAll(truncateTable);
  }
}

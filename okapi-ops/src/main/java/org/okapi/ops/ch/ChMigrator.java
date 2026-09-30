/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.ops.ch;

import com.clickhouse.client.api.Client;
import org.okapi.ch.tables.ChTableSpec;

public class ChMigrator {
  public static void migrate(Client client) {
    client.queryAll("CREATE DATABASE IF NOT EXISTS okapi_metrics ON CLUSTER 'okapi'");
    for (var tableSpec : ChTableSpec.getAllMetricLocalTables()) {
      client.queryAll(tableSpec);
    }
    for (var tableSpec : ChTableSpec.getAllMetricDistributedTables()) {
      client.queryAll(tableSpec);
    }
    client.queryAll("CREATE DATABASE IF NOT EXISTS okapi_traces ON CLUSTER 'okapi'");
    client.queryAll("CREATE DATABASE IF NOT EXISTS okapi_logs ON CLUSTER 'okapi'");
    for (var tableSpec : ChTableSpec.getAllNonMetricLocalTables()) {
      client.queryAll(tableSpec);
    }
    for (var tableSpec : ChTableSpec.getAllNonMetricDistributedTables()) {
      client.queryAll(tableSpec);
    }
  }
}

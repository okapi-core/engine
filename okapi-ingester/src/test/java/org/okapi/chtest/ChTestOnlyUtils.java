/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.chtest;

import com.clickhouse.client.api.Client;

public class ChTestOnlyUtils {
  public static void truncateTable(Client client, String table) {
    var truncateTable =
        "TRUNCATE TABLE IF EXISTS "
            + table
            + " ON CLUSTER 'okapi' SETTINGS distributed_ddl_task_timeout = 10";
    client.queryAll(truncateTable);
  }
}

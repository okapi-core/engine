/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.ops.ch;

import com.clickhouse.client.api.Client;
import org.okapi.ch.tables.ChTableSpec;

public class CreateChTablesSpec {
  public static String getCreateGaugeTableSpec() {
    return ChTableSpec.getGaugeTableSpec();
  }

  public static String getCreateHistoTableSpec() {
    return ChTableSpec.getHistoTableSpec();
  }

  public static String getCreateExponentialHistoTableSpec() {
    return ChTableSpec.getExponentialHistoTableSpec();
  }

  public static String getCreateSumTableSpec() {
    return ChTableSpec.getSumTableSpec();
  }

  public static String getCreateMetricEventsMetaTableSpec() {
    return ChTableSpec.getMetricEventsMetaTableSpec();
  }

  public static String getTracesTableSpec() {
    return ChTableSpec.getTracesTableSpec();
  }

  public static String getSpansIngestedAttribsTableSpec() {
    return ChTableSpec.getSpansIngestedAttribsTableSpec();
  }

  public static String getExemplarsTableSpec() {
    return ChTableSpec.getExemplarsTableSpec();
  }

  public static String getServiceRedEventsTableSpec() {
    return ChTableSpec.getServiceRedEventsTableSpec();
  }

  public static String getLogsTableSpec() {
    return ChTableSpec.getLogsTableSpec();
  }

  public static void migrate(Client client) {
    client.queryAll("CREATE DATABASE IF NOT EXISTS okapi_metrics");
    client.queryAll(getCreateGaugeTableSpec());
    client.queryAll(getCreateHistoTableSpec());
    client.queryAll(getCreateExponentialHistoTableSpec());
    client.queryAll(getCreateSumTableSpec());
    client.queryAll(getCreateMetricEventsMetaTableSpec());
    client.queryAll(getExemplarsTableSpec());
    client.queryAll("CREATE DATABASE IF NOT EXISTS okapi_traces");
    client.queryAll(getTracesTableSpec());
    client.queryAll(getSpansIngestedAttribsTableSpec());
    client.queryAll(getServiceRedEventsTableSpec());
    client.queryAll("CREATE DATABASE IF NOT EXISTS okapi_logs");
    client.queryAll(getLogsTableSpec());
  }
}

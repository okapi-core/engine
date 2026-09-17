/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.ops.ch;

import com.clickhouse.client.api.Client;
import org.okapi.resourcereader.ClasspathResourceReader;

public class CreateChTablesSpec {
  public static String getCreateGaugeTableSpec() {
    var metricsPath = "ch/create_metrics_table.sql";
    return ClasspathResourceReader.readResource(metricsPath);
  }

  public static String getCreateHistoTableSpec() {
    var metricsPath = "ch/create_histos_table.sql";
    return ClasspathResourceReader.readResource(metricsPath);
  }

  public static String getCreateSumTableSpec() {
    var metricsPath = "ch/create_sums_raw_samples.sql";
    return ClasspathResourceReader.readResource(metricsPath);
  }

  public static String getCreateMetricEventsMetaTableSpec() {
    var metricsPath = "ch/create_metric_events_stream_meta.sql";
    return ClasspathResourceReader.readResource(metricsPath);
  }

  public static String getTracesTableSpec() {
    var metricsPath = "ch/create_traces_table.sql";
    return ClasspathResourceReader.readResource(metricsPath);
  }

  public static String getSpansIngestedAttribsTableSpec() {
    var metricsPath = "ch/create_spans_ingested_attribs_table.sql";
    return ClasspathResourceReader.readResource(metricsPath);
  }

  public static String getExemplarsTableSpec() {
    return ClasspathResourceReader.readResource("ch/create_exemplar_table.sql");
  }

  public static String getServiceRedEventsTableSpec() {
    return ClasspathResourceReader.readResource("ch/create_service_red_events_table.sql");
  }

  public static String getLogsTableSpec() {
    return ClasspathResourceReader.readResource("ch/create_logs_table.sql");
  }

  public static void migrate(Client client) {
    client.queryAll("CREATE DATABASE IF NOT EXISTS okapi_metrics");
    client.queryAll(getCreateGaugeTableSpec());
    client.queryAll(getCreateHistoTableSpec());
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

/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.ch.tables;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Canonical ClickHouse table definitions shared by schema consumers. */
public final class ChTableSpec {
  private static final String RESOURCE_ROOT = "okapi-tables/ch/";

  private ChTableSpec() {}

  public static String getGaugeTableSpec() {
    return getGaugeDistributedTableSpec();
  }

  public static String getGaugeLocalTableSpec() {
    return readResource("create_metrics_local_table.sql");
  }

  public static String getGaugeDistributedTableSpec() {
    return readResource("create_metrics_distributed_table.sql");
  }

  public static String getHistoTableSpec() {
    return getHistoDistributedTableSpec();
  }

  public static String getHistoLocalTableSpec() {
    return readResource("create_histos_local_table.sql");
  }

  public static String getHistoDistributedTableSpec() {
    return readResource("create_histos_distributed_table.sql");
  }

  public static String getExponentialHistoTableSpec() {
    return getExponentialHistoDistributedTableSpec();
  }

  public static String getExponentialHistoLocalTableSpec() {
    return readResource("create_exponential_histos_local_table.sql");
  }

  public static String getExponentialHistoDistributedTableSpec() {
    return readResource("create_exponential_histos_distributed_table.sql");
  }

  public static String getSumTableSpec() {
    return getSumDistributedTableSpec();
  }

  public static String getSumLocalTableSpec() {
    return readResource("create_sums_raw_samples_local_table.sql");
  }

  public static String getSumDistributedTableSpec() {
    return readResource("create_sums_raw_samples_distributed_table.sql");
  }

  public static String getMetricEventsMetaTableSpec() {
    return getMetricEventsMetaDistributedTableSpec();
  }

  public static String getMetricEventsMetaLocalTableSpec() {
    return readResource("create_metric_events_stream_meta_local_table.sql");
  }

  public static String getMetricEventsMetaDistributedTableSpec() {
    return readResource("create_metric_events_stream_meta_distributed_table.sql");
  }

  public static String getExemplarsTableSpec() {
    return getExemplarsDistributedTableSpec();
  }

  public static String getExemplarsLocalTableSpec() {
    return readResource("create_exemplar_local_table.sql");
  }

  public static String getExemplarsDistributedTableSpec() {
    return readResource("create_exemplar_distributed_table.sql");
  }

  public static String getTracesTableSpec() {
    return getTracesDistributedTableSpec();
  }

  public static String getTracesLocalTableSpec() {
    return readResource("create_traces_local_table.sql");
  }

  public static String getTracesDistributedTableSpec() {
    return readResource("create_traces_distributed_table.sql");
  }

  public static String getSpansIngestedAttribsTableSpec() {
    return getSpansIngestedAttribsDistributedTableSpec();
  }

  public static String getSpansIngestedAttribsLocalTableSpec() {
    return readResource("create_spans_ingested_attribs_local_table.sql");
  }

  public static String getSpansIngestedAttribsDistributedTableSpec() {
    return readResource("create_spans_ingested_attribs_distributed_table.sql");
  }

  public static String getServiceRedEventsTableSpec() {
    return getServiceRedEventsDistributedTableSpec();
  }

  public static String getServiceRedEventsLocalTableSpec() {
    return readResource("create_service_red_events_local_table.sql");
  }

  public static String getServiceRedEventsDistributedTableSpec() {
    return readResource("create_service_red_events_distributed_table.sql");
  }

  public static String getLogsTableSpec() {
    return getLogsDistributedTableSpec();
  }

  public static String getLogsLocalTableSpec() {
    return readResource("create_logs_local_table.sql");
  }

  public static String getLogsDistributedTableSpec() {
    return readResource("create_logs_distributed_table.sql");
  }

  /** Returns every table definition in migration order. */
  public static List<String> getAllTables() {
    return List.of(
        getGaugeTableSpec(),
        getHistoTableSpec(),
        getExponentialHistoTableSpec(),
        getSumTableSpec(),
        getMetricEventsMetaTableSpec(),
        getExemplarsTableSpec(),
        getTracesTableSpec(),
        getSpansIngestedAttribsTableSpec(),
        getServiceRedEventsTableSpec(),
        getLogsTableSpec());
  }

  /** Returns the local metric tables that must exist before distributed tables are created. */
  public static List<String> getAllMetricLocalTables() {
    return List.of(
        getGaugeLocalTableSpec(),
        getHistoLocalTableSpec(),
        getExponentialHistoLocalTableSpec(),
        getSumLocalTableSpec(),
        getMetricEventsMetaLocalTableSpec(),
        getExemplarsLocalTableSpec());
  }

  /** Returns the distributed metric tables exposed to application queries and writes. */
  public static List<String> getAllMetricDistributedTables() {
    return List.of(
        getGaugeDistributedTableSpec(),
        getHistoDistributedTableSpec(),
        getExponentialHistoDistributedTableSpec(),
        getSumDistributedTableSpec(),
        getMetricEventsMetaDistributedTableSpec(),
        getExemplarsDistributedTableSpec());
  }

  public static List<String> getAllNonMetricLocalTables() {
    return List.of(
        getTracesLocalTableSpec(),
        getSpansIngestedAttribsLocalTableSpec(),
        getServiceRedEventsLocalTableSpec(),
        getLogsLocalTableSpec());
  }

  public static List<String> getAllNonMetricDistributedTables() {
    return List.of(
        getTracesDistributedTableSpec(),
        getSpansIngestedAttribsDistributedTableSpec(),
        getServiceRedEventsDistributedTableSpec(),
        getLogsDistributedTableSpec());
  }

  private static String readResource(String resourceName) {
    var resourcePath = RESOURCE_ROOT + resourceName;
    try (InputStream inputStream =
        ChTableSpec.class.getClassLoader().getResourceAsStream(resourcePath)) {
      if (inputStream == null) {
        throw new IllegalStateException("ClickHouse table resource not found: " + resourcePath);
      }
      return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException(
          "Failed to read ClickHouse table resource: " + resourcePath, e);
    }
  }
}

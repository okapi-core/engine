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
    return readResource("create_metrics_table.sql");
  }

  public static String getHistoTableSpec() {
    return readResource("create_histos_table.sql");
  }

  public static String getExponentialHistoTableSpec() {
    return readResource("create_exponential_histos_table.sql");
  }

  public static String getSumTableSpec() {
    return readResource("create_sums_raw_samples.sql");
  }

  public static String getMetricEventsMetaTableSpec() {
    return readResource("create_metric_events_stream_meta.sql");
  }

  public static String getExemplarsTableSpec() {
    return readResource("create_exemplar_table.sql");
  }

  public static String getTracesTableSpec() {
    return readResource("create_traces_table.sql");
  }

  public static String getSpansIngestedAttribsTableSpec() {
    return readResource("create_spans_ingested_attribs_table.sql");
  }

  public static String getServiceRedEventsTableSpec() {
    return readResource("create_service_red_events_table.sql");
  }

  public static String getLogsTableSpec() {
    return readResource("create_logs_table.sql");
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

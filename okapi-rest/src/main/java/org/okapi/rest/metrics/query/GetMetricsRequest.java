/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.metrics.query;

import jakarta.validation.constraints.NotNull;
import java.util.Map;
import lombok.*;
import org.springframework.ai.tool.annotation.ToolParam;

@AllArgsConstructor
@Builder(toBuilder = true)
@Getter
@NoArgsConstructor
@ToString
public class GetMetricsRequest {
  @ToolParam(description = "Name of the metric to query (e.g. \"http.server.duration\").")
  @NotNull(message = "Metrics name must be supplied.")
  @Getter
  String metric;

  @ToolParam(
      description =
          "Exact key-value label pairs the metric series must match. All entries must be present on the series (subset match).",
      required = false)
  @Getter
  Map<String, String> tags;

  @ToolParam(description = "Query window start time, in milliseconds since Unix epoch (inclusive)")
  @NotNull(message = "start time is required.")
  long start;

  @ToolParam(description = "Query window end time, in milliseconds since Unix epoch (exclusive).")
  @NotNull(message = "End time is required.")
  long end;

  @ToolParam(
      description =
"""
The type of metric to query. Acceptable values are:  GAUGE, HISTO, or SUM.
Only the type that is set will be queried.
e.g. if metricType is set to GAUGE, the metric is cpu_usage and tags is {"container": "container-abc"},
then query will look for a metric cpu_usage with tags container=container-abc and of type GAUGE.
`metric`, `tags` and `metricType` uniquely identify a metric.
  """)
  @NotNull(message = "metricType should be specified.")
  METRIC_TYPE metricType;

  @ToolParam(
      description =
"""
Configuration for gauge queries: time bucket resolution and aggregation function. Required when metricType is GAUGE.
Ignored for any other type. Conditionally required if metricType is GAUGE
  """,
      required = false)
  GaugeQueryConfig gaugeQueryConfig;

  @ToolParam(
      description =
"""
Configuration for histogram queries: temporality mode. Conditionally required when metricType is HISTO.
  """,
      required = false)
  HistoQueryConfig histoQueryConfig;

  @ToolParam(
      description =
"""
Configuration for sum queries: temporality mode. Conditionally required when metricType is SUM.
""",
      required = false)
  GetSumsQueryConfig sumsQueryConfig;
}

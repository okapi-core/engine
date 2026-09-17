/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.metrics.query;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import org.okapi.rest.promql.PromQlResultData;

@AllArgsConstructor
@Builder
@Getter
@JsonClassDescription("Time-series data returned for a single metric series query.")
@ToString
public class GetMetricsResponse {
  @JsonPropertyDescription("Name of the queried metric.")
  String metric;

  @JsonPropertyDescription("Label key-value pairs that identify the queried metric series.")
  Map<String, String> tags;

  @JsonPropertyDescription("Gauge time-series data. Populated only when metricType is GAUGE.")
  GetGaugeResponse gaugeResponse;

  @JsonPropertyDescription("Histogram data. Populated only when metricType is HISTO.")
  GetHistogramResponse histogramResponse;

  @JsonPropertyDescription("Sum/counter data. Populated only when metricType is SUM.")
  GetSumsResponse sumsResponse;

  @JsonPropertyDescription("PromQl response. Set only when the sent query was a promQL query.")
  PromQlResultData promqlResponse;
}

/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.search;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.Map;
import lombok.*;
import org.okapi.rest.metrics.query.METRIC_TYPE;

@AllArgsConstructor
@Getter
@NoArgsConstructor
@ToString
@EqualsAndHashCode
@Builder
@JsonClassDescription(
    "A unique metric series, identified by its name, type, and the set of labels associated with it.")
public class MetricPath {
  @JsonPropertyDescription("The metric name (e.g. http.server.duration).")
  String metric;

  @JsonPropertyDescription("The metric type: GAUGE, SUM, or HISTO.")
  METRIC_TYPE metricType;

  @JsonPropertyDescription("Temporality for SUM or HISTO metrics; null for GAUGE.")
  String temporality;

  @JsonPropertyDescription("Key-value label pairs that uniquely identify this metric series.")
  Map<String, String> labels;
}

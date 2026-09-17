/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.metrics.query;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;
import java.util.Map;
import lombok.*;

@AllArgsConstructor
@Builder
@Getter
@JsonClassDescription("A single gauge time-series for one unique set of tags.")
@ToString
@EqualsAndHashCode
public class GaugeSeries {
  @JsonPropertyDescription(
"""
Unit associated with the values. If this is null, it means the values are dimensionless.
""")
  String unit;

  @JsonPropertyDescription("Label key-value pairs identifying this series.")
  Map<String, String> tags;

  @JsonPropertyDescription("Ordered bucket start timestamps in milliseconds since Unix epoch.")
  List<Long> times;

  @JsonPropertyDescription("Aggregated gauge values per bucket, index-aligned with times.")
  List<Float> values;
}

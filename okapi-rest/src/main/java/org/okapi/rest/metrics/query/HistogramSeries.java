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
import lombok.NoArgsConstructor;

@AllArgsConstructor
@Getter
@Builder
@NoArgsConstructor
@JsonClassDescription("A histogram series for one unique set of tags.")
public class HistogramSeries {
  @JsonPropertyDescription("Label key-value pairs identifying this series.")
  Map<String, String> tags;

  @JsonPropertyDescription(
      "Unit for the buckets in this histogram. Note histogram counts are dimensionless.")
  String unit;

  @JsonPropertyDescription("List of histogram distributions for this series.")
  Histogram histogram;
}

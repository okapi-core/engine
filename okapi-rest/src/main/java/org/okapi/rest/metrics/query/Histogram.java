/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.metrics.query;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@Builder
@NoArgsConstructor
@Getter
@JsonClassDescription("A single histogram distribution covering a specific time window.")
public class Histogram {
  @JsonPropertyDescription("Window start time in milliseconds since Unix epoch.")
  long start;

  @JsonPropertyDescription("Window end time in milliseconds since Unix epoch.")
  Long end;

  @JsonPropertyDescription("Total number of observations recorded in this histogram.")
  Long count;

  @JsonPropertyDescription("Sum of all observed values.")
  Double sum;

  @JsonPropertyDescription("Per-bucket observation counts, index-aligned with buckets.")
  List<Long> counts;

  @JsonPropertyDescription("Upper-bound values defining each histogram bucket.")
  List<Float> buckets;
}

/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.metrics.query;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@AllArgsConstructor
@Getter
@Builder
@JsonClassDescription("A counter value accumulated over a specific time interval.")
@ToString
public class Sum {
  @JsonPropertyDescription("Interval start time in milliseconds since Unix epoch.")
  long ts;

  @JsonPropertyDescription("Interval end time in milliseconds since Unix epoch.")
  long te;

  @JsonPropertyDescription("Unit associated with this counter.")
  String unit;

  @JsonPropertyDescription("Accumulated counter value over this interval.")
  double count;
}

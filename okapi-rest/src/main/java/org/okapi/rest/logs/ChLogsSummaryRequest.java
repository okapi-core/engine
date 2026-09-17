/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.logs;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@AllArgsConstructor
@Builder
@Getter
@NoArgsConstructor
@ToString
@JsonClassDescription("Request for aggregate log analytics within a time window.")
public class ChLogsSummaryRequest {
  @JsonPropertyDescription("Inclusive start timestamp in nanoseconds since Unix epoch.")
  Long tsStartNanos;

  @JsonPropertyDescription("Inclusive end timestamp in nanoseconds since Unix epoch.")
  Long tsEndNanos;

  @JsonPropertyDescription(
      "Filters applied with AND semantics. Omit or provide an empty list to match all logs in the time window.")
  @Valid
  List<ChLogFilter> filters;

  @JsonPropertyDescription(
      "Timeline bucket width in milliseconds. Defaults to an automatically selected value.")
  @Min(value = 1000, message = "bucketMillis must be at least 1000")
  Long bucketMillis;

  @JsonPropertyDescription("Maximum number of services and streams to return. Defaults to 20.")
  @Min(value = 1, message = "limit must be greater than zero")
  Integer limit;
}

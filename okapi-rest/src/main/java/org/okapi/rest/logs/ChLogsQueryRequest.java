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
@JsonClassDescription("Request to search logs within a time window.")
public class ChLogsQueryRequest {
  @JsonPropertyDescription("Inclusive start timestamp in nanoseconds since Unix epoch.")
  Long tsStartNanos;

  @JsonPropertyDescription("Inclusive end timestamp in nanoseconds since Unix epoch.")
  Long tsEndNanos;

  @JsonPropertyDescription("Maximum number of log records to return.")
  @Min(value = 1, message = "limit must be greater than zero")
  Integer limit;

  @JsonPropertyDescription(
      "Filters applied with AND semantics. Omit or provide an empty list to match all logs in the time window.")
  @Valid
  List<ChLogFilter> filters;

  @JsonPropertyDescription(
      "Whether to include structured log record attributes in each returned row.")
  Boolean includeAttributes;

  @JsonPropertyDescription(
      "Whether to include structured resource attributes in each returned row.")
  Boolean includeResourceAttributes;

  @JsonPropertyDescription("Optional allow-list of log record attribute keys to return.")
  List<String> attributeKeys;

  @JsonPropertyDescription("Optional allow-list of resource attribute keys to return.")
  List<String> resourceAttributeKeys;
}

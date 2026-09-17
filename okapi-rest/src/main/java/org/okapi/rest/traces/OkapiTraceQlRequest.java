/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.traces;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
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
@JsonClassDescription("Querying traces using Okapi TraceQL.")
public class OkapiTraceQlRequest {
  @JsonPropertyDescription("Inclusive start timestamp in nanoseconds since Unix epoch.")
  Long tsStartNanos;

  @JsonPropertyDescription("Inclusive end timestamp in nanoseconds since Unix epoch.")
  Long tsEndNanos;

  @JsonPropertyDescription("Okapi TraceQL query that will be processed.")
  String traceQl;
}

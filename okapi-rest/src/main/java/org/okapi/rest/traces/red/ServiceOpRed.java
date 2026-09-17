/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.traces.red;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
@JsonClassDescription("RED metrics for one specific operation of the queried service.")
public class ServiceOpRed {
  @JsonPropertyDescription("Operation name (e.g. HTTP route, gRPC method, DB operation type).")
  String op;

  @JsonPropertyDescription("RED time-series metrics for this operation.")
  RedMetrics redMetrics;
}

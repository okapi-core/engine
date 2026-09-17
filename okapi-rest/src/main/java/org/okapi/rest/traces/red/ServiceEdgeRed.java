/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.traces.red;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
@JsonClassDescription(
    "RED metrics for calls from the queried service to one specific downstream peer.")
public class ServiceEdgeRed {
  @JsonPropertyDescription("Name of the downstream peer service.")
  @NotNull
  String peerService;

  @JsonPropertyDescription("RED time-series metrics for calls to this peer.")
  RedMetrics redMetrics;
}

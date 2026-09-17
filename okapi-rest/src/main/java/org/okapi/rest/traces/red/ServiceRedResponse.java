/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.traces.red;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
@JsonClassDescription(
    "RED metrics (Rate, Error rate, Duration) for a service, broken down by peer edges and operations.")
public class ServiceRedResponse {
  // RED for that specific service
  // RED for each operation
  // edge-RED for each of this service.
  // op-RED for operation level REDs
  @JsonPropertyDescription("Name of the service these RED metrics belong to.")
  String service;

  @JsonPropertyDescription("Aggregate RED metrics for the service as a whole.")
  RedMetrics serviceRed;

  @JsonPropertyDescription(
      "Per-downstream-peer RED metrics, one entry per peer service this service calls.")
  List<ServiceEdgeRed> peerReds;

  @JsonPropertyDescription("Per-operation RED metrics, one entry per detected operation.")
  List<ServiceOpRed> serviceOpReds;

  @JsonPropertyDescription(
      "Total number of distinct operations detected for this service in the queried window.")
  Integer totalDetectedOps;
}

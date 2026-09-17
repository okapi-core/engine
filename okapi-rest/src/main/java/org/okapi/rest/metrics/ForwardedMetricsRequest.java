/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.metrics;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@AllArgsConstructor
@Getter
@Builder
public class ForwardedMetricsRequest {
  int shardId;
  // todo: check that a Collection< > will also work here.
  List<ExportMetricsRequest> metricsRequests;
}

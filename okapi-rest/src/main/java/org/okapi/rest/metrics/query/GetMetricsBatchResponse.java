/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.metrics.query;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class GetMetricsBatchResponse {
  List<GetMetricsResponse> responses;
}

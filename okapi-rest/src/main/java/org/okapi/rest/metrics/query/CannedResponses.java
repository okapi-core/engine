/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.metrics.query;

import java.util.Map;

public class CannedResponses {
  public static GetMetricsResponse noMetricsResponse(String metric, Map<String, String> tags) {
    return GetMetricsResponse.builder().metric(metric).tags(tags).build();
  }
}

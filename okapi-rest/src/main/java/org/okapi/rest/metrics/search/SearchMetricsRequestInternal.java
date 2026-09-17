/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.metrics.search;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SearchMetricsRequestInternal {
  // use the search term to be tenantId:pattern
  String pattern;
  String tenantId;
  long startTime;
  long endTime;
}

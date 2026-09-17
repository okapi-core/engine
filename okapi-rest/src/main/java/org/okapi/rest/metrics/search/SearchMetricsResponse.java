/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.metrics.search;

import java.util.List;
import lombok.Builder;
import lombok.Getter;
import org.okapi.rest.metrics.MetricsPathSpecifier;

@Builder
@Getter
public class SearchMetricsResponse {
  List<MetricsPathSpecifier> results;
  int serverErrorCount;
  List<String> clientErrors;
}

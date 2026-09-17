/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.metrics.query;

import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.Value;
import org.okapi.rest.metrics.MetricsPathSpecifier;

@Builder
@Value
@Getter
public class ListMetricsResponse {
  List<MetricsPathSpecifier> results;
}

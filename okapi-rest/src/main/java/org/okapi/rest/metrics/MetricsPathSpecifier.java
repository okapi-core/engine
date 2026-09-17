/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.metrics;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
@AllArgsConstructor
public class MetricsPathSpecifier {
  String name;
  Map<String, String> tags;
  MetricType type;
}

/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.search;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.okapi.rest.TimeInterval;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
public class GetMetricNameHints {
  String metricPrefix;
  TimeInterval interval;
  MetricEventFilter metricEventFilter;
}

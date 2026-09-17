/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.search;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.okapi.rest.TimeInterval;

@AllArgsConstructor
@NoArgsConstructor
@Getter
public class GetTagHintsRequest {
  String metricName;
  Map<String, String> otherTags;
  String tagPrefix;
  TimeInterval interval;
  MetricEventFilter metricEventFilter;
}

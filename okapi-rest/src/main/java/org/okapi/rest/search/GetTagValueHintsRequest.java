/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.search;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.okapi.rest.TimeInterval;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
public class GetTagValueHintsRequest {
  String metricName;
  Map<String, String> otherTags;
  String tag;
  TimeInterval interval;
  MetricEventFilter metricEventFilter;
}

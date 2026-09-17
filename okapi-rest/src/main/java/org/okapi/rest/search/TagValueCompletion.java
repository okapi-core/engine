/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.search;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Getter
public class TagValueCompletion {
  String tag;
  List<String> candidates;
  MetricEventFilter metricEventFilter;
}

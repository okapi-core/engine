/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.metrics.ch.template;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@AllArgsConstructor
@Getter
@Builder
public class ChGetSumQueryTemplate {
  String table;
  String metric;
  Map<String, String> tags;
  String unit;
  String sumsType;
  long ts;
  long te;
}

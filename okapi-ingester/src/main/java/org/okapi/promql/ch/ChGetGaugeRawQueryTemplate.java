/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.ch;

import java.util.Map;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ChGetGaugeRawQueryTemplate {
  String table;
  String metric;
  long startMs;
  long endMs;
  Map<String, String> tags;
  String unit;
}

/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.ch;

import java.util.Map;
import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class ChGetExponentialHistoQueryTemplate {
  String table;
  String metric;
  Map<String, String> tags;
  String unit;
  String histoType;
  long ts;
  long te;
}

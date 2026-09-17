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
public class ChGetHistoQueryTemplate {
  String table;
  String metric;
  Map<String, String> tags;
  String unit;
  String histoType;
  long ts;
  long te;
}

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
public class ChGetGaugeQueryTemplate {
  String table;
  String bucketExpr;
  String aggExpr;
  String metric;
  long startMs;
  long endMs;
  Map<String, String> tags;
}

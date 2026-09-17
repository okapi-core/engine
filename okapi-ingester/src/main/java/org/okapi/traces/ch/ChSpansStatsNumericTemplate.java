/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.traces.ch;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ChSpansStatsNumericTemplate {
  String table;
  List<String> attributes;
  String bucketStartExpr;
  Map<String, String> aggClauses;
  List<ColValueFilter> colFilters;
  List<String> rawClauses;
}

/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.traces.ch;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ChSpanNumericalAttributeSummaryTemplate {
  String table;
  String attribute;
  String bucketStartExpr;
  String aggClause;
  String presenceClause;
  String whereClause;
}

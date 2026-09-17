/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.eval.nodes;

import java.util.List;
import org.okapi.promql.parse.LabelMatcher;

public final class SelectorExpr implements LogicalExpr {
  public final String metricOrNull;
  public final List<LabelMatcher> matchers;
  public final Long atTsMs;
  public final DurationExpr offset;

  public SelectorExpr(
      String metricOrNull, List<LabelMatcher> matchers, Long atTsMs, DurationExpr offset) {
    this.metricOrNull = metricOrNull;
    this.matchers = matchers;
    this.atTsMs = atTsMs;
    this.offset = offset;
  }
}

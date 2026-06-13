/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.eval.nodes;

import java.util.List;
import lombok.AllArgsConstructor;
import org.okapi.promql.parse.LabelMatcher;

@AllArgsConstructor
public final class SelectorExpr implements LogicalExpr {
  public final String metricOrNull;
  public final List<LabelMatcher> matchers;
  public final Long atTsMs;
  public final Long offsetMs;
}

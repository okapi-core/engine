/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.eval.nodes;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public final class SubqueryExpr implements LogicalExpr {
  public final LogicalExpr inner;
  public final long rangeMs;
  public final long stepMs;
  public final Long offsetMs;
}

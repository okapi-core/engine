/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.eval.nodes;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public final class LiteralExpr implements LogicalExpr {
  public final float value;
}

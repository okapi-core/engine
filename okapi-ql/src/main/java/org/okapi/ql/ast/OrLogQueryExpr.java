/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.ql.ast;

import java.util.List;
import lombok.Value;

@Value
public final class OrLogQueryExpr implements LogQueryExpr {
  List<LogQueryExpr> children;

  public OrLogQueryExpr(List<LogQueryExpr> children) {
    this.children = children;
  }
}

/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.ql.ast;

import java.util.List;
import lombok.Value;

@Value
public class AndLogQueryExpr implements LogQueryExpr {
  List<LogQueryExpr> children;

  public AndLogQueryExpr(List<LogQueryExpr> children) {
    this.children = children;
  }
}

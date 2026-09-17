/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.ql.parse;

import org.okapi.ql.ast.LogQueryExpr;

public final class OkapiTraceQlParser {
  private OkapiTraceQlParser() {}

  public static LogQueryExpr parse(String expr) {
    return OkapiQlParser.parse(expr);
  }
}

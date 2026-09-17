/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.engine.ch;

import org.okapi.exceptions.BadRequestException;
import org.okapi.ql.ast.LogQueryExpr;
import org.okapi.ql.parse.OkapiTraceQlParser;
import org.springframework.stereotype.Component;

@Component
public class ChTraceQlParser {
  public LogQueryExpr parseExpression(String traceQl) {
    if (traceQl == null || traceQl.isBlank()) {
      throw new BadRequestException("traceQl is required");
    }
    try {
      return OkapiTraceQlParser.parse(traceQl);
    } catch (IllegalArgumentException e) {
      throw new BadRequestException(e.getMessage());
    }
  }
}

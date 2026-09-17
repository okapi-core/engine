/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.engine.ch;

import org.okapi.exceptions.BadRequestException;
import org.okapi.ql.ast.LogQueryExpr;
import org.okapi.ql.parse.OkapiQlParser;
import org.springframework.stereotype.Component;

@Component
public class ChLogQlParser {
  public LogQueryExpr parseExpression(String logQl) {
    if (logQl == null || logQl.isBlank()) {
      throw new BadRequestException("logQl is required");
    }
    try {
      return OkapiQlParser.parse(logQl);
    } catch (IllegalArgumentException e) {
      throw new BadRequestException(e.getMessage());
    }
  }
}

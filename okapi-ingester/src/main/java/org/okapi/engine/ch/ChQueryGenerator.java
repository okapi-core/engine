/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.engine.ch;

import org.springframework.stereotype.Component;

@Component
public class ChQueryGenerator {
  public String generate(ChQueryModel.LogQuery queryNode) {
    var visitor = new ChExprQueryWriter();
    visitor.visit(queryNode);
    return visitor.getQuery();
  }
}

/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.okapi.promql.parser.PromQLLexer;

public class PromQLMain {

  public static void main(String[] args) {
    var example = "day_of_month(timestamp(up{job=\"prometheus\"}))";
    var stream = CharStreams.fromString(example);
    var lexer = new PromQLLexer(stream);
    var tokens = new CommonTokenStream(lexer);
  }
}

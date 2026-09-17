/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.ql.parse;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.okapi.ql.ast.LogQueryExpr;
import org.okapi.ql.parser.OkapiLogQlLexer;
import org.okapi.ql.parser.OkapiLogQlParser;

public final class OkapiQlParser {
  private OkapiQlParser() {}

  public static LogQueryExpr parse(String expr) {
    var lexer = new OkapiLogQlLexer(CharStreams.fromString(expr));
    lexer.removeErrorListeners();
    lexer.addErrorListener(ThrowingErrorListener.INSTANCE);

    var tokens = new CommonTokenStream(lexer);
    var parser = new OkapiLogQlParser(tokens);
    parser.removeErrorListeners();
    parser.addErrorListener(ThrowingErrorListener.INSTANCE);

    return new OkapiQlAstVisitor().visitQuery(parser.query());
  }

  private static final class ThrowingErrorListener extends BaseErrorListener {
    private static final ThrowingErrorListener INSTANCE = new ThrowingErrorListener();

    @Override
    public void syntaxError(
        Recognizer<?, ?> recognizer,
        Object offendingSymbol,
        int line,
        int charPositionInLine,
        String msg,
        RecognitionException e) {
      throw new IllegalArgumentException(
          "invalid Okapi QL at " + line + ":" + charPositionInLine + ": " + msg, e);
    }
  }
}

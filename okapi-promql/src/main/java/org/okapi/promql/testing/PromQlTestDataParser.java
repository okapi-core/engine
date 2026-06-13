/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.testing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.okapi.promql.testing.PromQlTestAst.*;
import org.okapi.promql.testing.PromQlTestDataTokenizer.Token;
import org.okapi.promql.testing.PromQlTestDataTokenizer.TokenType;

public final class PromQlTestDataParser {
  private final PromQlTestDataTokenizer tokenizer;
  private final String[] lines;

  public PromQlTestDataParser(String input) {
    this.tokenizer = new PromQlTestDataTokenizer(input);
    this.lines = splitLines(input);
  }

  public TestFile parse() {
    List<Command> commands = new ArrayList<>();
    skipNewlines();
    while (!check(TokenType.EOF)) {
      commands.add(parseCommand());
      skipNewlines();
    }
    return new TestFile(commands);
  }

  private Command parseCommand() {
    if (match(TokenType.KEYWORD_CLEAR)) {
      return new ClearCmd();
    }
    if (match(TokenType.KEYWORD_LOAD)) {
      return parseLoad(false);
    }
    if (match(TokenType.KEYWORD_LOAD_WITH_NHCB)) {
      return parseLoad(true);
    }
    if (match(TokenType.KEYWORD_EVAL)) {
      return parseEval(LegacyEvalModifier.NONE);
    }
    if (match(TokenType.KEYWORD_EVAL_FAIL)) {
      return parseEval(LegacyEvalModifier.FAIL);
    }
    if (match(TokenType.KEYWORD_EVAL_WARN)) {
      return parseEval(LegacyEvalModifier.WARN);
    }
    if (match(TokenType.KEYWORD_EVAL_INFO)) {
      return parseEval(LegacyEvalModifier.INFO);
    }
    if (match(TokenType.KEYWORD_EVAL_ORDERED)) {
      return parseEval(LegacyEvalModifier.ORDERED);
    }
    throw error(peek(), "expected command");
  }

  private LoadCmd parseLoad(boolean withNhcb) {
    DurationLiteral step = parseDurationLiteral();
    expect(TokenType.NEWLINE, "expected newline after load header");
    List<SeriesDef> series = new ArrayList<>();
    while (true) {
      if (check(TokenType.NEWLINE)) {
        if (isBlockEnd()) {
          break;
        }
        next();
        continue;
      }
      if (isCommandStart(peek())) {
        break;
      }
      series.add(parseSeriesDefLine());
      expectLineEnd("expected newline after series line");
      if (isBlockEnd()) {
        break;
      }
    }
    return new LoadCmd(step, withNhcb, series);
  }

  private EvalCmd parseEval(LegacyEvalModifier modifier) {
    EvalType evalType;
    if (match(TokenType.KEYWORD_INSTANT)) {
      expect(TokenType.KEYWORD_AT, "expected 'at' after instant");
      DurationLiteral at = parseDurationLiteral();
      String expr = parseExpressionUntilNewline();
      evalType = new InstantEval(at);
      List<Expectation> expectations = new ArrayList<>();
      List<ExpectedResult> results = new ArrayList<>();
      parseEvalBody(expectations, results);
      return new EvalCmd(evalType, expr, modifier, expectations, results);
    }
    if (match(TokenType.KEYWORD_RANGE)) {
      expect(TokenType.KEYWORD_FROM, "expected 'from' after range");
      DurationLiteral from = parseDurationLiteral();
      expect(TokenType.KEYWORD_TO, "expected 'to' after range from");
      DurationLiteral to = parseDurationLiteral();
      expect(TokenType.KEYWORD_STEP, "expected 'step' after range to");
      DurationLiteral step = parseDurationLiteral();
      String expr = parseExpressionUntilNewline();
      evalType = new RangeEval(from, to, step);
      List<Expectation> expectations = new ArrayList<>();
      List<ExpectedResult> results = new ArrayList<>();
      parseEvalBody(expectations, results);
      return new EvalCmd(evalType, expr, modifier, expectations, results);
    }
    throw error(peek(), "expected instant or range eval");
  }

  private void parseEvalBody(List<Expectation> expectations, List<ExpectedResult> results) {
    expect(TokenType.NEWLINE, "expected newline after eval header");
    while (true) {
      if (check(TokenType.NEWLINE)) {
        if (isBlockEnd()) {
          break;
        }
        next();
        continue;
      }
      if (isCommandStart(peek())) {
        break;
      }
      if (check(TokenType.KEYWORD_EXPECT)) {
        expectations.add(parseExpectationLine());
      } else if (check(TokenType.KEYWORD_EXPECTED_FAIL_MESSAGE)) {
        expectations.add(parseLegacyFailMessage());
      } else if (check(TokenType.KEYWORD_EXPECTED_FAIL_REGEXP)) {
        expectations.add(parseLegacyFailRegexp());
      } else if (isScalarLineStart(peek())) {
        results.add(parseScalarResult());
      } else {
        results.add(new SeriesResult(parseSeriesDefLine()));
      }
      expectLineEnd("expected newline after eval line");
      if (isBlockEnd()) {
        break;
      }
    }
  }

  private Expectation parseExpectationLine() {
    expect(TokenType.KEYWORD_EXPECT, "expected expect");
    if (match(TokenType.KEYWORD_RANGE)) {
      expect(TokenType.KEYWORD_VECTOR, "expected 'vector' after 'expect range'");
      expect(TokenType.KEYWORD_FROM, "expected 'from' after 'expect range vector'");
      DurationLiteral from = parseDurationLiteral();
      expect(TokenType.KEYWORD_TO, "expected 'to' after range vector from");
      DurationLiteral to = parseDurationLiteral();
      expect(TokenType.KEYWORD_STEP, "expected 'step' after range vector to");
      DurationLiteral step = parseDurationLiteral();
      consumeLineRemainder();
      return new ExpectRangeVector(from, to, step);
    }
    if (match(TokenType.KEYWORD_STRING)) {
      Token value = expect(TokenType.STRING, "expected string literal after 'expect string'");
      consumeLineRemainder();
      return new ExpectString(value.lexeme());
    }
    ExpectType type = parseExpectType();
    MatchType matchType = null;
    String pattern = null;
    if (check(TokenType.IDENT) && isMatchType(peek().lexeme())) {
      matchType = "msg".equals(peek().lexeme()) ? MatchType.MSG : MatchType.REGEX;
      Token matchToken = next();
      expect(TokenType.COLON, "expected ':' after " + matchToken.lexeme());
      pattern = readRestOfLine(matchToken.line(), peek());
      consumeLineRemainder();
    } else {
      consumeLineRemainder();
    }
    return new ExpectAnnotation(type, matchType, pattern);
  }

  private Expectation parseLegacyFailMessage() {
    Token token = expect(TokenType.KEYWORD_EXPECTED_FAIL_MESSAGE, "expected expected_fail_message");
    String message = readRestOfLine(token.line(), peek());
    consumeLineRemainder();
    return new ExpectFailMessage(message);
  }

  private Expectation parseLegacyFailRegexp() {
    Token token = expect(TokenType.KEYWORD_EXPECTED_FAIL_REGEXP, "expected expected_fail_regexp");
    String pattern = readRestOfLine(token.line(), peek());
    consumeLineRemainder();
    return new ExpectFailRegexp(pattern);
  }

  private ExpectedResult parseScalarResult() {
    PointExpr point = parsePointExpr();
    if (!check(TokenType.NEWLINE)) {
      throw error(peek(), "expected scalar result line to end");
    }
    if (point instanceof NumberPoint np) {
      return new ScalarResult(np.value());
    }
    if (point instanceof NaNPoint) {
      return new ScalarResult(Double.NaN);
    }
    if (point instanceof InfPoint inf) {
      return new ScalarResult(inf.negative() ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY);
    }
    throw error(peek(), "invalid scalar result");
  }

  private SeriesDef parseSeriesDefLine() {
    String metric = null;
    Map<String, String> labels = new HashMap<>();
    if (!check(TokenType.LBRACE)) {
      metric = parseMetricName();
    }
    if (check(TokenType.LBRACE)) {
      labels = parseLabelSet();
    }
    List<PointExpr> points = new ArrayList<>();
    while (!check(TokenType.NEWLINE) && !check(TokenType.EOF)) {
      points.add(parsePointExpr());
    }
    return new SeriesDef(metric, labels, points);
  }

  private String parseMetricName() {
    StringBuilder sb = new StringBuilder();
    while (!check(TokenType.NEWLINE)
        && !check(TokenType.EOF)
        && !check(TokenType.LBRACE)
        && !isPointStart(peek())) {
      Token token = next();
      sb.append(token.lexeme());
    }
    if (sb.length() == 0) {
      throw error(peek(), "expected metric name");
    }
    return sb.toString();
  }

  private Map<String, String> parseLabelSet() {
    expect(TokenType.LBRACE, "expected '{' to start labels");
    Map<String, String> labels = new HashMap<>();
    if (match(TokenType.RBRACE)) {
      return labels;
    }
    while (true) {
      String key = parseIdentLike();
      expect(TokenType.EQUALS, "expected '=' after label name");
      Token value = expect(TokenType.STRING, "expected string label value");
      labels.put(key, value.lexeme());
      if (match(TokenType.COMMA)) {
        continue;
      }
      expect(TokenType.RBRACE, "expected '}' after labels");
      break;
    }
    return labels;
  }

  private PointExpr parsePointExpr() {
    PointExpr base = parsePointBase();
    if (match(TokenType.PLUS)) {
      PointExpr step = parsePointBase();
      int count = parseRepeatCount();
      return new StepSequencePoint(base, step, count);
    }
    if (check(TokenType.MINUS) && isAdjacent(previous(), peek())) {
      next();
      PointExpr step = parsePointBase();
      if (!(step instanceof NumberPoint number)) {
        throw error(previous(), "expected numeric step after '-'");
      }
      int count = parseRepeatCount();
      return new StepSequencePoint(base, new NumberPoint(-number.value()), count);
    }
    if (isRepeatToken(peek())) {
      int count = parseRepeatCount();
      return new RepeatPoint(base, count);
    }
    return base;
  }

  private int parseRepeatCount() {
    if (check(TokenType.IDENT)) {
      String lexeme = peek().lexeme();
      if (lexeme.startsWith("x") && lexeme.length() > 1 && isDigits(lexeme.substring(1))) {
        next();
        return Integer.parseInt(lexeme.substring(1));
      }
    }
    expectIdent("x");
    return parseIntNumber();
  }

  private boolean isRepeatToken(Token token) {
    if (token.type() != TokenType.IDENT) {
      return false;
    }
    String lexeme = token.lexeme();
    return "x".equals(lexeme) || (lexeme.startsWith("x") && lexeme.length() > 1 && isDigits(lexeme.substring(1)));
  }

  private PointExpr parsePointBase() {
    if (match(TokenType.MISSING)) {
      return parseMissingPoint();
    }
    if (match(TokenType.KEYWORD_STALE)) {
      return parseStalePoint();
    }
    if (match(TokenType.KEYWORD_NAN)) {
      return parseNaNPoint();
    }
    if (match(TokenType.KEYWORD_INF)) {
      return parseInfPoint(false);
    }
    if (match(TokenType.MINUS)) {
      if (match(TokenType.KEYWORD_INF)) {
        return parseInfPoint(true);
      }
      Token number = expect(TokenType.NUMBER, "expected number after '-'");
      return parseNumberPoint(-parseDouble(number.lexeme()));
    }
    if (match(TokenType.PLUS)) {
      if (match(TokenType.KEYWORD_INF)) {
        return parseInfPoint(false);
      }
      Token number = expect(TokenType.NUMBER, "expected number after '+'");
      return parseNumberPoint(parseDouble(number.lexeme()));
    }
    if (match(TokenType.NUMBER)) {
      Token number = previous();
      return parseNumberPoint(parseDouble(number.lexeme()));
    }
    if (match(TokenType.DOUBLE_LBRACE)) {
      return parseHistogramPoint(parseHistogramLiteral());
    }
    throw error(peek(), "expected point");
  }

  private PointExpr parseNumberPoint(double value) {
    return new NumberPoint(value);
  }

  private PointExpr parseMissingPoint() {
    return new MissingPoint();
  }

  private PointExpr parseStalePoint() {
    return new StalePoint();
  }

  private PointExpr parseNaNPoint() {
    return new NaNPoint();
  }

  private PointExpr parseInfPoint(boolean negative) {
    return new InfPoint(negative);
  }

  private PointExpr parseHistogramPoint(HistogramLiteral literal) {
    return new HistogramPoint(literal);
  }

  private HistogramLiteral parseHistogramLiteral() {
    Map<String, HistogramValue> fields = new HashMap<>();
    while (!check(TokenType.DOUBLE_RBRACE)) {
      String key = parseIdentLike();
      expect(TokenType.COLON, "expected ':' after histogram field");
      HistogramValue value = parseHistogramValue();
      fields.put(key, value);
    }
    expect(TokenType.DOUBLE_RBRACE, "expected '}}' to end histogram");
    return new HistogramLiteral(fields);
  }

  private HistogramValue parseHistogramValue() {
    if (match(TokenType.LBRACKET)) {
      List<Double> values = new ArrayList<>();
      if (!check(TokenType.RBRACKET)) {
        while (true) {
          values.add(parseSignedNumber());
          if (match(TokenType.COMMA)) {
            continue;
          }
          if (check(TokenType.RBRACKET)) {
            break;
          }
        }
      }
      expect(TokenType.RBRACKET, "expected ']'");
      return new HistogramNumberList(values);
    }
    if (check(TokenType.MINUS) || check(TokenType.NUMBER)) {
      return new HistogramNumber(parseSignedNumber());
    }
    String ident = parseIdentLike();
    return new HistogramIdentifier(ident);
  }

  private String parseExpressionUntilNewline() {
    Token start = peek();
    String expr = readRestOfLine(start.line(), start);
    consumeLineRemainder();
    return expr;
  }

  private DurationLiteral parseDurationLiteral() {
    if (match(TokenType.DURATION)) {
      return new DurationLiteral(previous().lexeme());
    }
    if (match(TokenType.NUMBER)) {
      return new DurationLiteral(previous().lexeme());
    }
    throw error(peek(), "expected duration");
  }

  private ExpectType parseExpectType() {
    if (match(TokenType.KEYWORD_ORDERED)) {
      return ExpectType.ORDERED;
    }
    if (match(TokenType.KEYWORD_FAIL)) {
      return ExpectType.FAIL;
    }
    if (match(TokenType.KEYWORD_WARN)) {
      return ExpectType.WARN;
    }
    if (match(TokenType.KEYWORD_INFO)) {
      return ExpectType.INFO;
    }
    if (match(TokenType.KEYWORD_NO_WARN)) {
      return ExpectType.NO_WARN;
    }
    if (match(TokenType.KEYWORD_NO_INFO)) {
      return ExpectType.NO_INFO;
    }
    throw error(peek(), "expected expect type");
  }

  private String parseIdentLike() {
    if (match(TokenType.IDENT)) {
      return previous().lexeme();
    }
    Token token = peek();
    if (isIdentLikeToken(token.type())) {
      next();
      return token.lexeme();
    }
    throw error(peek(), "expected identifier");
  }

  private boolean isIdentLikeToken(TokenType type) {
    return type.name().startsWith("KEYWORD_");
  }

  private void expectIdent(String value) {
    Token token = next();
    if (!(token.type() == TokenType.IDENT || isIdentLikeToken(token.type()))
        || !value.equals(token.lexeme())) {
      throw error(token, "expected '" + value + "'");
    }
  }

  private int parseIntNumber() {
    Token token = expect(TokenType.NUMBER, "expected integer");
    return Integer.parseInt(token.lexeme());
  }

  private double parseSignedNumber() {
    if (match(TokenType.MINUS)) {
      Token number = expect(TokenType.NUMBER, "expected number after '-'");
      return -parseDouble(number.lexeme());
    }
    Token number = expect(TokenType.NUMBER, "expected number");
    return parseDouble(number.lexeme());
  }

  private double parseDouble(String lexeme) {
    return Double.parseDouble(lexeme);
  }

  private boolean isPointStart(Token token) {
    return switch (token.type()) {
      case NUMBER,
          MINUS,
          PLUS,
          MISSING,
          KEYWORD_STALE,
          KEYWORD_NAN,
          KEYWORD_INF,
          DOUBLE_LBRACE ->
          true;
      default -> false;
    };
  }

  private boolean isScalarLineStart(Token token) {
    return isPointStart(token);
  }

  private boolean isMatchType(String lexeme) {
    return "msg".equals(lexeme) || "regex".equals(lexeme);
  }

  private boolean isDigits(String value) {
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c < '0' || c > '9') {
        return false;
      }
    }
    return !value.isEmpty();
  }

  private boolean isAdjacent(Token left, Token right) {
    return left.line() == right.line() && left.column() + left.lexeme().length() == right.column();
  }

  private boolean isCommandStart(Token token) {
    return switch (token.type()) {
      case KEYWORD_CLEAR,
          KEYWORD_LOAD,
          KEYWORD_LOAD_WITH_NHCB,
          KEYWORD_EVAL,
          KEYWORD_EVAL_FAIL,
          KEYWORD_EVAL_WARN,
          KEYWORD_EVAL_INFO,
          KEYWORD_EVAL_ORDERED ->
          true;
      default -> false;
    };
  }

  private boolean isBlockEnd() {
    if (check(TokenType.EOF)) {
      return true;
    }
    if (check(TokenType.NEWLINE)) {
      return true;
    }
    return isCommandStart(peek());
  }

  private void consumeLineRemainder() {
    while (!check(TokenType.NEWLINE) && !check(TokenType.EOF)) {
      next();
    }
  }

  private String readRestOfLine(int lineNumber, Token startToken) {
    String lineText = lineText(lineNumber);
    int startIndex = Math.max(0, startToken.column() - 1);
    if (startIndex >= lineText.length()) {
      return "";
    }
    return lineText.substring(startIndex).trim();
  }

  private void skipNewlines() {
    while (match(TokenType.NEWLINE)) {
      // skip
    }
  }

  private boolean match(TokenType type) {
    if (check(type)) {
      next();
      return true;
    }
    return false;
  }

  private boolean check(TokenType type) {
    return peek().type() == type;
  }

  private Token expect(TokenType type, String message) {
    if (check(type)) {
      return next();
    }
    throw error(peek(), message);
  }

  private void expectLineEnd(String message) {
    if (match(TokenType.NEWLINE) || check(TokenType.EOF)) return;
    throw error(peek(), message);
  }

  private Token next() {
    last = tokenizer.next();
    return last;
  }

  private Token peek() {
    return tokenizer.peek();
  }

  private Token previous() {
    return last;
  }

  private Token last = new Token(TokenType.EOF, "", 0, 0);

  private RuntimeException error(Token token, String message) {
    return new IllegalArgumentException(
        "Parse error at line " + token.line() + ", col " + token.column() + ": " + message);
  }

  private String lineText(int lineNumber) {
    if (lineNumber <= 0 || lineNumber > lines.length) {
      return "";
    }
    String line = lines[lineNumber - 1];
    if (line.endsWith("\r")) {
      return line.substring(0, line.length() - 1);
    }
    return line;
  }

  private static String[] splitLines(String input) {
    return (input == null ? "" : input).split("\n", -1);
  }
}

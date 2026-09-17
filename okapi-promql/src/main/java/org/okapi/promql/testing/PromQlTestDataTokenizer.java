/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.testing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PromQlTestDataTokenizer {
  public enum TokenType {
    EOF,
    NEWLINE,
    IDENT,
    NUMBER,
    DURATION,
    STRING,
    MISSING,
    LBRACE,
    RBRACE,
    DOUBLE_LBRACE,
    DOUBLE_RBRACE,
    LBRACKET,
    RBRACKET,
    LPAREN,
    RPAREN,
    COMMA,
    COLON,
    EQUALS,
    PLUS,
    MINUS,
    STAR,
    SLASH,
    KEYWORD_LOAD,
    KEYWORD_LOAD_WITH_NHCB,
    KEYWORD_CLEAR,
    KEYWORD_EVAL,
    KEYWORD_EVAL_FAIL,
    KEYWORD_EVAL_WARN,
    KEYWORD_EVAL_INFO,
    KEYWORD_EVAL_ORDERED,
    KEYWORD_INSTANT,
    KEYWORD_RANGE,
    KEYWORD_AT,
    KEYWORD_FROM,
    KEYWORD_TO,
    KEYWORD_STEP,
    KEYWORD_EXPECT,
    KEYWORD_STRING,
    KEYWORD_VECTOR,
    KEYWORD_ORDERED,
    KEYWORD_FAIL,
    KEYWORD_WARN,
    KEYWORD_INFO,
    KEYWORD_NO_WARN,
    KEYWORD_NO_INFO,
    KEYWORD_EXPECTED_FAIL_MESSAGE,
    KEYWORD_EXPECTED_FAIL_REGEXP,
    KEYWORD_STALE,
    KEYWORD_NAN,
    KEYWORD_INF
  }

  public record Token(TokenType type, String lexeme, int line, int column) {}

  private static final Map<String, TokenType> KEYWORDS = new HashMap<>();

  static {
    KEYWORDS.put("load", TokenType.KEYWORD_LOAD);
    KEYWORDS.put("load_with_nhcb", TokenType.KEYWORD_LOAD_WITH_NHCB);
    KEYWORDS.put("clear", TokenType.KEYWORD_CLEAR);
    KEYWORDS.put("eval", TokenType.KEYWORD_EVAL);
    KEYWORDS.put("eval_fail", TokenType.KEYWORD_EVAL_FAIL);
    KEYWORDS.put("eval_warn", TokenType.KEYWORD_EVAL_WARN);
    KEYWORDS.put("eval_info", TokenType.KEYWORD_EVAL_INFO);
    KEYWORDS.put("eval_ordered", TokenType.KEYWORD_EVAL_ORDERED);
    KEYWORDS.put("instant", TokenType.KEYWORD_INSTANT);
    KEYWORDS.put("range", TokenType.KEYWORD_RANGE);
    KEYWORDS.put("at", TokenType.KEYWORD_AT);
    KEYWORDS.put("from", TokenType.KEYWORD_FROM);
    KEYWORDS.put("to", TokenType.KEYWORD_TO);
    KEYWORDS.put("step", TokenType.KEYWORD_STEP);
    KEYWORDS.put("expect", TokenType.KEYWORD_EXPECT);
    KEYWORDS.put("string", TokenType.KEYWORD_STRING);
    KEYWORDS.put("vector", TokenType.KEYWORD_VECTOR);
    KEYWORDS.put("ordered", TokenType.KEYWORD_ORDERED);
    KEYWORDS.put("fail", TokenType.KEYWORD_FAIL);
    KEYWORDS.put("warn", TokenType.KEYWORD_WARN);
    KEYWORDS.put("info", TokenType.KEYWORD_INFO);
    KEYWORDS.put("no_warn", TokenType.KEYWORD_NO_WARN);
    KEYWORDS.put("no_info", TokenType.KEYWORD_NO_INFO);
    KEYWORDS.put("expected_fail_message", TokenType.KEYWORD_EXPECTED_FAIL_MESSAGE);
    KEYWORDS.put("expected_fail_regexp", TokenType.KEYWORD_EXPECTED_FAIL_REGEXP);
    KEYWORDS.put("stale", TokenType.KEYWORD_STALE);
    KEYWORDS.put("NaN", TokenType.KEYWORD_NAN);
    KEYWORDS.put("Inf", TokenType.KEYWORD_INF);
    KEYWORDS.put("inf", TokenType.KEYWORD_INF);
  }

  private final String input;
  private final int length;
  private int index;
  private int line;
  private int column;
  private boolean atLineStart;
  private Token buffered;

  public PromQlTestDataTokenizer(String input) {
    this.input = input == null ? "" : input;
    this.length = this.input.length();
    this.index = 0;
    this.line = 1;
    this.column = 1;
    this.atLineStart = true;
  }

  public Token peek() {
    if (buffered == null) {
      buffered = nextInternal();
    }
    return buffered;
  }

  public Token next() {
    if (buffered != null) {
      Token token = buffered;
      buffered = null;
      return token;
    }
    return nextInternal();
  }

  public List<Token> tokenize() {
    List<Token> tokens = new ArrayList<>();
    Token token;
    do {
      token = next();
      tokens.add(token);
    } while (token.type() != TokenType.EOF);
    return tokens;
  }

  private Token nextInternal() {
    skipWhitespaceAndComments();
    if (index >= length) {
      return new Token(TokenType.EOF, "", line, column);
    }

    char c = input.charAt(index);
    if (c == '\n' || c == '\r') {
      return readNewline();
    }

    if (c == '"' || c == '`') {
      return readString();
    }

    if (c == '{') {
      if (peekChar(1) == '{') {
        return readDoubleBrace(TokenType.DOUBLE_LBRACE, "{{");
      }
      return readSingleChar(TokenType.LBRACE);
    }
    if (c == '}') {
      if (peekChar(1) == '}') {
        return readDoubleBrace(TokenType.DOUBLE_RBRACE, "}}");
      }
      return readSingleChar(TokenType.RBRACE);
    }
    if (c == '[') {
      return readSingleChar(TokenType.LBRACKET);
    }
    if (c == ']') {
      return readSingleChar(TokenType.RBRACKET);
    }
    if (c == '(') {
      return readSingleChar(TokenType.LPAREN);
    }
    if (c == ')') {
      return readSingleChar(TokenType.RPAREN);
    }
    if (c == ',') {
      return readSingleChar(TokenType.COMMA);
    }
    if (c == ':') {
      return readSingleChar(TokenType.COLON);
    }
    if (c == '=') {
      return readSingleChar(TokenType.EQUALS);
    }
    if (c == '+') {
      return readSingleChar(TokenType.PLUS);
    }
    if (c == '-') {
      return readSingleChar(TokenType.MINUS);
    }
    if (c == '*') {
      return readSingleChar(TokenType.STAR);
    }
    if (c == '/') {
      return readSingleChar(TokenType.SLASH);
    }

    if (c == '_' && !isIdentPart(peekChar(1))) {
      return readSingleChar(TokenType.MISSING);
    }

    if (isNumberStart(c) || (c == '.' && isDigit(peekChar(1)))) {
      return readNumberOrDuration();
    }

    if (isIdentStart(c)) {
      return readIdentOrKeyword();
    }

    return readSingleChar(TokenType.IDENT);
  }

  private void skipWhitespaceAndComments() {
    boolean lineHasOnlyWhitespace = atLineStart;
    while (index < length) {
      char c = input.charAt(index);
      if (c == ' ' || c == '\t' || c == '\f') {
        advance();
        continue;
      }
      if (c == '\n' || c == '\r') {
        return;
      }
      if (lineHasOnlyWhitespace && c == '#') {
        while (index < length && input.charAt(index) != '\n' && input.charAt(index) != '\r') {
          advance();
        }
        lineHasOnlyWhitespace = true;
        continue;
      }
      lineHasOnlyWhitespace = false;
      return;
    }
  }

  private Token readNewline() {
    int startLine = line;
    int startColumn = column;
    char c = input.charAt(index);
    if (c == '\r' && peekChar(1) == '\n') {
      advance();
    }
    advance();
    line++;
    column = 1;
    atLineStart = true;
    return new Token(TokenType.NEWLINE, "\\n", startLine, startColumn);
  }

  private Token readString() {
    int startLine = line;
    int startColumn = column;
    char quote = input.charAt(index);
    advance();
    StringBuilder sb = new StringBuilder();
    while (index < length) {
      char c = input.charAt(index);
      if (c == quote) {
        advance();
        return new Token(TokenType.STRING, sb.toString(), startLine, startColumn);
      }
      if (quote == '"' && c == '\\' && peekChar(1) != '\0') {
        sb.append(c);
        advance();
        c = input.charAt(index);
      }
      sb.append(c);
      advance();
    }
    return new Token(TokenType.STRING, sb.toString(), startLine, startColumn);
  }

  private Token readIdentOrKeyword() {
    int start = index;
    int startLine = line;
    int startColumn = column;
    advance();
    while (isIdentPart(peekChar(0))) {
      advance();
    }
    String lexeme = input.substring(start, index);
    TokenType keywordType = KEYWORDS.get(lexeme);
    if (keywordType != null) {
      return new Token(keywordType, lexeme, startLine, startColumn);
    }
    return new Token(TokenType.IDENT, lexeme, startLine, startColumn);
  }

  private Token readNumberOrDuration() {
    int start = index;
    int startLine = line;
    int startColumn = column;
    boolean sawUnit = false;
    while (true) {
      readNumberPart();
      int unitStart = index;
      if (readDurationUnit()) {
        sawUnit = true;
      }
      if (!sawUnit || !isNumberStart(peekChar(0))) {
        break;
      }
    }
    String lexeme = input.substring(start, index);
    TokenType type = sawUnit ? TokenType.DURATION : TokenType.NUMBER;
    return new Token(type, lexeme, startLine, startColumn);
  }

  private void readNumberPart() {
    if (peekChar(0) == '+' || peekChar(0) == '-') {
      advance();
    }
    boolean sawDot = false;
    while (index < length) {
      char c = input.charAt(index);
      if (c >= '0' && c <= '9') {
        advance();
        continue;
      }
      if (c == '.' && !sawDot) {
        sawDot = true;
        advance();
        continue;
      }
      break;
    }
    if (peekChar(0) == 'e' || peekChar(0) == 'E') {
      advance();
      if (peekChar(0) == '+' || peekChar(0) == '-') {
        advance();
      }
      while (peekChar(0) >= '0' && peekChar(0) <= '9') {
        advance();
      }
    }
  }

  private Token readDoubleBrace(TokenType type, String lexeme) {
    int startLine = line;
    int startColumn = column;
    advance();
    advance();
    return new Token(type, lexeme, startLine, startColumn);
  }

  private Token readSingleChar(TokenType type) {
    int startLine = line;
    int startColumn = column;
    String lexeme = String.valueOf(input.charAt(index));
    advance();
    return new Token(type, lexeme, startLine, startColumn);
  }

  private void advance() {
    index++;
    column++;
    atLineStart = false;
  }

  private char peekChar(int offset) {
    int pos = index + offset;
    if (pos < 0 || pos >= length) {
      return '\0';
    }
    return input.charAt(pos);
  }

  private static boolean isIdentStart(char c) {
    return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || c == '_';
  }

  private static boolean isIdentPart(char c) {
    return isIdentStart(c) || (c >= '0' && c <= '9');
  }

  private static boolean isNumberStart(char c) {
    return (c >= '0' && c <= '9');
  }

  private static boolean isDigit(char c) {
    return (c >= '0' && c <= '9');
  }

  private boolean readDurationUnit() {
    if (peekChar(0) == 'm' && peekChar(1) == 's') {
      advance();
      advance();
      return true;
    }
    char unit = peekChar(0);
    if (unit == 's' || unit == 'm' || unit == 'h' || unit == 'd' || unit == 'w' || unit == 'y') {
      advance();
      return true;
    }
    return false;
  }
}

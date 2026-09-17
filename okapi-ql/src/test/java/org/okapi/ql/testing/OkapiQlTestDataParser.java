/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.ql.testing;

import java.util.ArrayList;
import java.util.List;
import org.okapi.ql.testing.OkapiQlTestAst.FailCase;
import org.okapi.ql.testing.OkapiQlTestAst.ParseCase;
import org.okapi.ql.testing.OkapiQlTestAst.TestCase;
import org.okapi.ql.testing.OkapiQlTestAst.TestFile;

public final class OkapiQlTestDataParser {
  private final List<String> lines;
  private int pos;

  public OkapiQlTestDataParser(String input) {
    lines = input.lines().toList();
  }

  public TestFile parse() {
    var cases = new ArrayList<TestCase>();
    skipBlankAndCommentLines();
    while (!eof()) {
      cases.add(parseCase());
      skipBlankAndCommentLines();
    }
    return new TestFile(cases);
  }

  private TestCase parseCase() {
    var header = next();
    if (header.startsWith("parse ")) {
      return new ParseCase(nameAfter("parse ", header), parseQueryBlock());
    }
    if (header.startsWith("fail ")) {
      return new FailCase(nameAfter("fail ", header), parseQueryBlock());
    }
    throw error("expected parse/fail case header");
  }

  private String parseQueryBlock() {
    expectLine("query");

    var query = new StringBuilder();
    while (!eof() && !peek().trim().equals("end")) {
      query.append(next()).append('\n');
    }
    expectLine("end");
    return query.toString().trim();
  }

  private void skipBlankAndCommentLines() {
    while (!eof()) {
      var trimmed = peek().trim();
      if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
        return;
      }
      pos++;
    }
  }

  private void expectLine(String expected) {
    if (eof()) {
      throw error("expected " + expected);
    }
    var actual = next().trim();
    if (!expected.equals(actual)) {
      throw error("expected " + expected + ", got " + actual);
    }
  }

  private static String nameAfter(String prefix, String line) {
    var name = line.substring(prefix.length()).trim();
    if (name.isEmpty()) {
      throw new IllegalArgumentException("test case name is required");
    }
    return name;
  }

  private String peek() {
    return lines.get(pos);
  }

  private String next() {
    return lines.get(pos++);
  }

  private boolean eof() {
    return pos >= lines.size();
  }

  private IllegalArgumentException error(String message) {
    return new IllegalArgumentException(message + " at line " + (pos + 1));
  }
}

/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.ql;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.okapi.ql.ast.AndLogQueryExpr;
import org.okapi.ql.ast.ComparisonLogQueryExpr;
import org.okapi.ql.ast.ComparisonOp;
import org.okapi.ql.ast.CountByOp;
import org.okapi.ql.ast.IntegerValue;
import org.okapi.ql.ast.MapFieldRef;
import org.okapi.ql.ast.QueryLogQueryExpr;
import org.okapi.ql.ast.StringValue;
import org.okapi.ql.parse.OkapiQlParser;
import org.okapi.ql.testing.OkapiQlTestAst.FailCase;
import org.okapi.ql.testing.OkapiQlTestAst.ParseCase;
import org.okapi.ql.testing.OkapiQlTestDataParser;

class OkapiQlParserTest {

  @Test
  void parserCorpus() throws Exception {
    for (var resource : corpusResources()) {
      var testFile = new OkapiQlTestDataParser(readResource(resource)).parse();
      for (var testCase : testFile.getCases()) {
        var label = resource + ": " + testCase.getName();
        if (testCase instanceof ParseCase parseCase) {
          assertDoesNotThrow(() -> OkapiQlParser.parse(parseCase.getQuery()), label);
        } else if (testCase instanceof FailCase failCase) {
          assertThrows(
              IllegalArgumentException.class,
              () -> OkapiQlParser.parse(failCase.getQuery()),
              label);
        }
      }
    }
  }

  @Test
  void parsesGenericMapStyleFilterAst() {
    var parsed =
        assertInstanceOf(
            QueryLogQueryExpr.class,
            OkapiQlParser.parse("labels['service.name'] = 'checkout' and fields['status'] >= 500"));
    var and = assertInstanceOf(AndLogQueryExpr.class, parsed.getFilter());
    var service = assertInstanceOf(ComparisonLogQueryExpr.class, and.getChildren().get(0));
    var status = assertInstanceOf(ComparisonLogQueryExpr.class, and.getChildren().get(1));

    var serviceField = assertInstanceOf(MapFieldRef.class, service.getField());
    assertEquals(List.of("labels"), serviceField.getPath());
    assertEquals("service.name", serviceField.getKey());
    assertEquals(ComparisonOp.EQ, service.getOp());
    assertEquals(new StringValue("checkout"), service.getValues().getFirst());

    var statusField = assertInstanceOf(MapFieldRef.class, status.getField());
    assertEquals(List.of("fields"), statusField.getPath());
    assertEquals("status", statusField.getKey());
    assertEquals(ComparisonOp.GTE, status.getOp());
    assertEquals(new IntegerValue(500), status.getValues().getFirst());
  }

  @Test
  void parsesCountByAggregationAst() {
    var parsed =
        assertInstanceOf(
            QueryLogQueryExpr.class,
            OkapiQlParser.parse(
                "level >= warn and fields['route'] = '/checkout' | count by (service, level, fields['status'])"));

    assertEquals(1, parsed.getPipeline().size());
    var countBy = assertInstanceOf(CountByOp.class, parsed.getPipeline().getFirst());
    assertEquals(List.of("service"), countBy.getFields().get(0).getPath());
    assertEquals(List.of("level"), countBy.getFields().get(1).getPath());
    var statusField = assertInstanceOf(MapFieldRef.class, countBy.getFields().get(2));
    assertEquals(List.of("fields"), statusField.getPath());
    assertEquals("status", statusField.getKey());
  }

  @Test
  void parsesUppercaseAndAsKeywordAst() {
    var parsed =
        assertInstanceOf(
            QueryLogQueryExpr.class, OkapiQlParser.parse("level = info AND service = checkout"));
    var and = assertInstanceOf(AndLogQueryExpr.class, parsed.getFilter());
    assertEquals(2, and.getChildren().size());
  }

  @Test
  void decodesStringEscapesWithJdk() {
    var parsed =
        assertInstanceOf(QueryLogQueryExpr.class, OkapiQlParser.parse("message = 'line\\nnext'"));
    var comparison = assertInstanceOf(ComparisonLogQueryExpr.class, parsed.getFilter());

    assertEquals(new StringValue("line\nnext"), comparison.getValues().getFirst());
  }

  private static List<String> corpusResources() {
    return List.of(
        "okapiqltest/testdata/parser.test",
        "okapiqltest/testdata/pipelines.test",
        "okapiqltest/testdata/map_fields.test");
  }

  private static String readResource(String resource) throws Exception {
    try (var in = OkapiQlParserTest.class.getClassLoader().getResourceAsStream(resource)) {
      if (in == null) {
        throw new IllegalStateException(resource + " not found");
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}

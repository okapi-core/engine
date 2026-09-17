/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.ql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.okapi.ql.ast.AndLogQueryExpr;
import org.okapi.ql.ast.ComparisonLogQueryExpr;
import org.okapi.ql.ast.ComparisonOp;
import org.okapi.ql.ast.CountByOp;
import org.okapi.ql.ast.DurationValue;
import org.okapi.ql.ast.MapFieldRef;
import org.okapi.ql.ast.PathFieldRef;
import org.okapi.ql.ast.QueryLogQueryExpr;
import org.okapi.ql.ast.StringValue;
import org.okapi.ql.parse.OkapiTraceQlParser;

class OkapiTraceQlParserTest {

  @Test
  void parsesTraceFieldsAndPipelines() {
    var parsed =
        assertInstanceOf(
            QueryLogQueryExpr.class,
            OkapiTraceQlParser.parse(
                "service = 'checkout' and duration > 250ms | count by (service, status)"));

    var and = assertInstanceOf(AndLogQueryExpr.class, parsed.getFilter());
    var service = assertInstanceOf(ComparisonLogQueryExpr.class, and.getChildren().get(0));
    var duration = assertInstanceOf(ComparisonLogQueryExpr.class, and.getChildren().get(1));

    assertEquals(new PathFieldRef(List.of("service")), service.getField());
    assertEquals(ComparisonOp.EQ, service.getOp());
    assertEquals(new StringValue("checkout"), service.getValues().getFirst());
    assertEquals(new PathFieldRef(List.of("duration")), duration.getField());
    assertEquals(ComparisonOp.GT, duration.getOp());
    assertEquals(new DurationValue("250ms"), duration.getValues().getFirst());

    var countBy = assertInstanceOf(CountByOp.class, parsed.getPipeline().getFirst());
    assertEquals(new PathFieldRef(List.of("service")), countBy.getFields().get(0));
    assertEquals(new PathFieldRef(List.of("status")), countBy.getFields().get(1));
  }

  @Test
  void parsesSpanAttributes() {
    var parsed =
        assertInstanceOf(
            QueryLogQueryExpr.class,
            OkapiTraceQlParser.parse("attributes['http.route'] = '/checkout'"));
    var comparison = assertInstanceOf(ComparisonLogQueryExpr.class, parsed.getFilter());
    var field = assertInstanceOf(MapFieldRef.class, comparison.getField());

    assertEquals(List.of("attributes"), field.getPath());
    assertEquals("http.route", field.getKey());
  }
}

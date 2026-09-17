/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.engine.ch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.okapi.engine.ch.ChQueryModel.ColumnExpr;
import org.okapi.engine.ch.ChQueryModel.RawExpr;
import org.okapi.engine.ch.ChTraceQlTranslationBridge.ValueHint;
import org.okapi.exceptions.BadRequestException;
import org.okapi.ql.ast.MapFieldRef;
import org.okapi.ql.ast.PathFieldRef;

class ChTraceQlTranslationBridgeTests {
  private final ChTraceQlTranslationBridge bridge = new ChTraceQlTranslationBridge();

  @Test
  void resolvesKnownTraceFieldsToColumns() {
    var service = bridge.resolve(new PathFieldRef(List.of("service")), ValueHint.UNKNOWN);
    var httpStatus =
        bridge.resolve(
            new PathFieldRef(List.of("http", "response", "status_code")), ValueHint.UNKNOWN);

    assertEquals("service_name", ((ColumnExpr) service.getExpression()).getExpression());
    assertEquals("http_status_code", ((ColumnExpr) httpStatus.getExpression()).getExpression());
  }

  @Test
  void resolvesDurationToSpanWidth() {
    var duration = bridge.resolve(new PathFieldRef(List.of("duration")), ValueHint.UNKNOWN);

    assertEquals("(ts_end_ns - ts_start_ns)", ((RawExpr) duration.getExpression()).getExpression());
  }

  @Test
  void resolvesTypedAttributeBuckets() {
    var mapping =
        bridge.resolve(new MapFieldRef(List.of("attributes"), "custom.number"), ValueHint.NUMBER);

    assertTrue(
        ((ColumnExpr) mapping.getExpression()).getExpression().startsWith("attribs_number_"));
    assertTrue(
        ((ColumnExpr) mapping.getExpression()).getExpression().endsWith("['custom.number']"));
  }

  @Test
  void resolvesUnknownAttributeAsStringOrNumberProjection() {
    var mapping =
        bridge.resolve(new MapFieldRef(List.of("attributes"), "custom.number"), ValueHint.UNKNOWN);

    var expr = ((RawExpr) mapping.getExpression()).getExpression();
    assertTrue(expr.contains("attribs_str_"));
    assertTrue(expr.contains("attribs_number_"));
    assertTrue(expr.contains("toString("));
  }

  @Test
  void rejectsResourceAttributesUntilStored() {
    assertThrows(
        BadRequestException.class,
        () ->
            bridge.resolve(new MapFieldRef(List.of("resource"), "service.name"), ValueHint.STRING));
  }
}

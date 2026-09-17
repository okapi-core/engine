/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.engine.ch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.okapi.engine.ch.ChLogsTranslationBridge.ValueHint;
import org.okapi.engine.ch.ChQueryModel.ColumnExpr;
import org.okapi.engine.ch.ChQueryModel.RawExpr;
import org.okapi.ql.ast.MapFieldRef;
import org.okapi.ql.ast.PathFieldRef;

class ChLogsTranslationBridgeTests {
  private final ChLogsTranslationBridge bridge = new ChLogsTranslationBridge();

  @Test
  void resolvesKnownLogFieldsToColumns() {
    var service = bridge.resolve(new PathFieldRef(List.of("service")), ValueHint.UNKNOWN);
    var body = bridge.resolve(new PathFieldRef(List.of("message")), ValueHint.UNKNOWN);

    assertEquals("service_name", ((ColumnExpr) service.getExpression()).getExpression());
    assertEquals("body", ((ColumnExpr) body.getExpression()).getExpression());
  }

  @Test
  void resolvesTypedAttributeBuckets() {
    var mapping =
        bridge.resolve(new MapFieldRef(List.of("fields"), "http.status_code"), ValueHint.NUMBER);

    assertTrue(
        ((ColumnExpr) mapping.getExpression()).getExpression().startsWith("attribs_number_"));
    assertTrue(
        ((ColumnExpr) mapping.getExpression()).getExpression().endsWith("['http.status_code']"));
  }

  @Test
  void resolvesUnknownAttributeAsStringOrNumberProjection() {
    var mapping =
        bridge.resolve(new MapFieldRef(List.of("fields"), "http.status_code"), ValueHint.UNKNOWN);

    var expr = ((RawExpr) mapping.getExpression()).getExpression();
    assertTrue(expr.contains("attribs_str_"));
    assertTrue(expr.contains("attribs_number_"));
    assertTrue(expr.contains("toString("));
  }
}

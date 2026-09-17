/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.engine.ch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.okapi.ql.parse.OkapiTraceQlParser;
import org.okapi.rest.traces.OkapiTraceQlResultKind;

class ChTraceQlTranslatorTests {
  private final ChTraceQlTranslator translator =
      new ChTraceQlTranslator(new ChTraceQlTranslationBridge());
  private final ChQueryGenerator generator = new ChQueryGenerator();

  @Test
  void translatesFiltersDurationAndLimit() {
    var ast =
        OkapiTraceQlParser.parse(
            "service = 'checkout-api' and duration > 250ms and http.response.status_code >= 500 | limit 5");

    var query = translator.translateTraceQl(ast, 10L, 20L);
    var sql = generator.generate(query);

    assertEquals(OkapiTraceQlResultKind.SPAN_ROWS, query.getTraceResultKind());
    assertTrue(sql.contains("ts_start_ns >= 10"));
    assertTrue(sql.contains("ts_end_ns <= 20"));
    assertTrue(sql.contains("service_name = 'checkout-api'"));
    assertTrue(sql.contains("(ts_end_ns - ts_start_ns) > 250000000"));
    assertTrue(sql.contains("http_status_code >= 500"));
    assertTrue(sql.endsWith("LIMIT 5"));
  }

  @Test
  void translatesAttributesAndStatus() {
    var ast = OkapiTraceQlParser.parse("attributes['custom.number'] = 42 and status = error");

    var sql = generator.generate(translator.translateTraceQl(ast, null, null));

    assertTrue(sql.contains("attribs_number_"));
    assertTrue(sql.contains("['custom.number'] = 42"));
    assertTrue(sql.contains("span_status = 'ERROR'"));
  }

  @Test
  void translatesCountByAsGroupedRows() {
    var ast =
        OkapiTraceQlParser.parse("service = 'checkout-api' | count by (service, status) | limit 7");

    var query = translator.translateTraceQl(ast, null, null);
    var sql = generator.generate(query);

    assertEquals(OkapiTraceQlResultKind.COUNT_BY, query.getTraceResultKind());
    assertTrue(sql.startsWith("SELECT service_name AS service, span_status AS status"));
    assertTrue(sql.contains("count() AS count"));
    assertTrue(sql.contains("GROUP BY service_name, span_status"));
    assertTrue(sql.contains("ORDER BY count DESC"));
    assertTrue(sql.endsWith("LIMIT 7"));
  }

  @Test
  void translatesSelectAndSort() {
    var ast =
        OkapiTraceQlParser.parse(
            "service = checkout | select(service, duration) | sort duration asc");

    var query = translator.translateTraceQl(ast, null, null);
    var sql = generator.generate(query);

    assertEquals(OkapiTraceQlResultKind.TABLE, query.getTraceResultKind());
    assertTrue(
        sql.startsWith("SELECT service_name AS service, (ts_end_ns - ts_start_ns) AS duration"));
    assertTrue(sql.contains(" AS duration"));
    assertTrue(sql.contains("ORDER BY (ts_end_ns - ts_start_ns) ASC"));
    assertTrue(sql.endsWith("ASC LIMIT 1000"));
  }
}

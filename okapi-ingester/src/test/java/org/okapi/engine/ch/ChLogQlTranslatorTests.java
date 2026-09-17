/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.engine.ch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.okapi.ql.parse.OkapiQlParser;
import org.okapi.rest.logs.OkapiLogQlResultKind;

class ChLogQlTranslatorTests {
  private final ChLogQlTranslator translator = new ChLogQlTranslator(new ChLogsTranslationBridge());
  private final ChQueryGenerator generator = new ChQueryGenerator();

  @Test
  void translatesFiltersAndLimit() {
    var ast =
        OkapiQlParser.parse(
            "service = 'checkout-api' and fields['http.status_code'] = 200 | limit 5");

    var query = translator.translateLogQl(ast, 10L, 20L);
    var sql = generator.generate(query);

    assertEquals(OkapiLogQlResultKind.LOG_ROWS, query.getResultKind());
    assertTrue(sql.contains("ts_ns >= 10"));
    assertTrue(sql.contains("ts_ns <= 20"));
    assertTrue(sql.contains("service_name = 'checkout-api'"));
    assertTrue(sql.contains("attribs_number_"));
    assertTrue(sql.contains("['http.status_code'] = 200"));
    assertTrue(sql.endsWith("LIMIT 5"));
  }

  @Test
  void translatesCountByAsGroupedRows() {
    var ast =
        OkapiQlParser.parse(
            "level >= 13 | count by (service, fields['http.status_code']) | limit 7");

    var query = translator.translateLogQl(ast, null, null);
    var sql = generator.generate(query);

    assertEquals(OkapiLogQlResultKind.COUNT_BY, query.getResultKind());
    assertTrue(sql.startsWith("SELECT service_name AS service, multiIf("));
    assertTrue(sql.contains("count() AS count"));
    assertTrue(sql.contains("GROUP BY service_name, multiIf("));
    assertTrue(sql.contains("ORDER BY count DESC"));
    assertTrue(sql.endsWith("LIMIT 7"));
  }
}

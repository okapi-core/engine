/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.ch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.okapi.ch.ChSqlEscaper;
import org.okapi.metrics.ch.template.ChMetricTemplateEngine;

class ChSqlEscaperTests {
  @Test
  void escapesClickHouseStringLiteralCharacters() {
    assertEquals("O\\'Reilly\\\\tmp\\nnext\\rline\\tend\\0", ChSqlEscaper.escapeLiteral("O'Reilly\\tmp\nnext\rline\tend\0"));
  }

  @Test
  void escapedValuesRenderAsSingleClickHouseLiterals() {
    var query =
        new ChMetricTemplateEngine()
            .render(
                "get_gauge_raw_samples_exact_match.jte",
                ChGetGaugeRawQueryTemplate.builder()
                    .table("gauges")
                    .metric(ChSqlEscaper.escapeLiteral("cpu' OR 1 = 1 --"))
                    .tags(ChSqlEscaper.escapeTags(Map.of("owner", "O'Reilly\\ops")))
                    .startMs(1)
                    .endMs(2)
                    .build());

    assertTrue(query.contains("metric = 'cpu\\' OR 1 = 1 --'"), query);
    assertTrue(query.contains("tags['owner'] = 'O\\'Reilly\\\\ops'"), query);
  }
}

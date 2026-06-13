/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.ch;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.okapi.metrics.ch.template.ChGetHistoQueryTemplate;
import org.okapi.metrics.ch.template.ChGetSumQueryTemplate;
import org.okapi.metrics.ch.template.ChMetricTemplateEngine;

class ChPromQlExactMatchTemplateTests {
  private final ChMetricTemplateEngine engine = new ChMetricTemplateEngine();

  @Test
  void promQlSampleQueriesRequireExactLabelSets() {
    var tags = Map.of("env", "dev", "host", "a");

    assertExactMatch(
        engine.render(
            "get_gauge_raw_samples_exact_match.jte",
            ChGetGaugeRawQueryTemplate.builder()
                .table("gauges")
                .metric("cpu")
                .tags(tags)
                .startMs(1)
                .endMs(2)
                .build()));
    assertExactMatch(
        engine.render(
            "get_histo_samples_exact_match.jte",
            ChGetHistoQueryTemplate.builder()
                .table("histograms")
                .metric("latency")
                .tags(tags)
                .histoType("DELTA")
                .ts(1)
                .te(2)
                .build()));
    assertExactMatch(
        engine.render(
            "get_sum_samples_exact_match.jte",
            ChGetSumQueryTemplate.builder()
                .table("sums")
                .metric("requests")
                .tags(tags)
                .sumsType("DELTA")
                .ts(1)
                .te(2)
                .build()));
    assertExactMatch(
        engine.render(
            "get_metric_event_type_exact_match.jte",
            ChMetricEventTypeQueryTemplate.builder()
                .table("events")
                .metric("cpu")
                .tags(tags)
                .startMs(1)
                .endMs(2)
                .build()));
  }

  private static void assertExactMatch(String query) {
    assertTrue(query.contains("AND length(tags) = 2"), query);
    assertTrue(query.contains("mapContains(tags, 'env')"), query);
    assertTrue(query.contains("mapContains(tags, 'host')"), query);
  }
}

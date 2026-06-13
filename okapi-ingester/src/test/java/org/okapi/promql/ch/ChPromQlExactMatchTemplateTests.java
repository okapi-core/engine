/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.ch;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
                .unit("seconds")
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
                .unit("seconds")
                .histoType("DELTA")
                .ts(1)
                .te(2)
                .build()));
    assertExactMatch(
        engine.render(
            "get_exponential_histo_samples_exact_match.jte",
            ChGetExponentialHistoQueryTemplate.builder()
                .table("exponential_histograms")
                .metric("latency")
                .tags(tags)
                .unit("seconds")
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
                .unit("seconds")
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
                .unit("seconds")
                .startMs(1)
                .endMs(2)
                .build()));
  }

  @Test
  void seriesDiscoverySelectsUnitMetadata() {
    var query =
        engine.render(
            "get_metric_events_series.jte",
            ChSeriesDiscoveryQueryTemplate.builder()
                .table("events")
                .metric("cpu")
                .startMs(1)
                .endMs(2)
                .build());

    assertTrue(query.contains("SELECT DISTINCT metric, tags, unit"), query);
  }

  @Test
  void internalLabelsAreSeparatedFromPersistedTags() {
    var labels =
        ChPromQlTsClient.splitLabels(
            Map.of(
                "env", "dev",
                "__name__", "cpu",
                "__type__", "gauge",
                "__unit__", "seconds"));

    assertEquals("seconds", labels.unit());
    assertEquals(Map.of("env", "dev"), labels.tags());

    var labelsWithoutUnit = ChPromQlTsClient.splitLabels(Map.of("env", "dev"));
    assertEquals("", labelsWithoutUnit.unit());
    assertEquals(Map.of("env", "dev"), labelsWithoutUnit.tags());
  }

  private static void assertExactMatch(String query) {
    assertTrue(query.contains("AND unit = 'seconds'"), query);
    assertTrue(query.contains("AND length(tags) = 2"), query);
    assertTrue(query.contains("mapContains(tags, 'env')"), query);
    assertTrue(query.contains("mapContains(tags, 'host')"), query);
  }
}

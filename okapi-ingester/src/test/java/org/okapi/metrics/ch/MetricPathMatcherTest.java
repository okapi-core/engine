/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.metrics.ch;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.okapi.rest.search.LabelValueFilter;
import org.okapi.rest.search.LabelValuePatternFilter;
import org.okapi.rest.search.SearchMetricsRequest;

public class MetricPathMatcherTest {

  // --- metric name (exact) ---

  @Test
  void metricName_matches() {
    var matcher = matcherFor(req().metricName("cpu.usage").build());
    assertTrue(matcher.matches("cpu.usage", Map.of()));
  }

  @Test
  void metricName_doesNotMatch() {
    var matcher = matcherFor(req().metricName("cpu.usage").build());
    assertFalse(matcher.matches("mem.usage", Map.of()));
  }

  // --- metric pattern (regex) ---

  @Test
  void metricPattern_matches() {
    var matcher = matcherFor(req().metricNamePattern("cpu\\..*").build());
    assertTrue(matcher.matches("cpu.usage", Map.of()));
    assertTrue(matcher.matches("cpu.idle", Map.of()));
  }

  @Test
  void metricPattern_doesNotMatch() {
    var matcher = matcherFor(req().metricNamePattern("cpu\\..*").build());
    assertFalse(matcher.matches("mem.usage", Map.of()));
  }

  // --- label value (exact) ---

  @Test
  void labelValue_matches() {
    var matcher = matcherFor(req().valueFilters(List.of(filter("env", "prod"))).build());
    assertTrue(matcher.matches("any.metric", Map.of("env", "prod", "region", "us-east")));
  }

  @Test
  void labelValue_wrongValue() {
    var matcher = matcherFor(req().valueFilters(List.of(filter("env", "prod"))).build());
    assertFalse(matcher.matches("any.metric", Map.of("env", "dev")));
  }

  @Test
  void labelValue_missingKey() {
    var matcher = matcherFor(req().valueFilters(List.of(filter("env", "prod"))).build());
    assertFalse(matcher.matches("any.metric", Map.of("region", "us-east")));
  }

  // --- label value pattern (regex) ---

  @Test
  void labelValuePattern_matches() {
    var matcher = matcherFor(req().patternFilters(List.of(patFilter("host", "web-.*"))).build());
    assertTrue(matcher.matches("any.metric", Map.of("host", "web-01")));
    assertTrue(matcher.matches("any.metric", Map.of("host", "web-99")));
  }

  @Test
  void labelValuePattern_doesNotMatch() {
    var matcher = matcherFor(req().patternFilters(List.of(patFilter("host", "web-.*"))).build());
    assertFalse(matcher.matches("any.metric", Map.of("host", "db-01")));
  }

  @Test
  void labelValuePattern_missingKey() {
    var matcher = matcherFor(req().patternFilters(List.of(patFilter("host", "web-.*"))).build());
    assertFalse(matcher.matches("any.metric", Map.of("region", "us-east")));
  }

  // --- AND semantics ---

  @Test
  void andSemantics_allFiltersMustMatch() {
    var matcher =
        matcherFor(
            req()
                .metricNamePattern("cpu\\..*")
                .valueFilters(List.of(filter("env", "prod")))
                .patternFilters(List.of(patFilter("host", "web-.*")))
                .build());

    assertTrue(matcher.matches("cpu.usage", Map.of("env", "prod", "host", "web-01")));
  }

  @Test
  void andSemantics_metricPatternFails() {
    var matcher =
        matcherFor(
            req()
                .metricNamePattern("cpu\\..*")
                .valueFilters(List.of(filter("env", "prod")))
                .build());

    assertFalse(matcher.matches("mem.usage", Map.of("env", "prod")));
  }

  @Test
  void andSemantics_labelValueFails() {
    var matcher =
        matcherFor(
            req().metricName("cpu.usage").valueFilters(List.of(filter("env", "prod"))).build());

    assertFalse(matcher.matches("cpu.usage", Map.of("env", "dev")));
  }

  @Test
  void andSemantics_labelPatternFails() {
    var matcher =
        matcherFor(
            req()
                .metricName("cpu.usage")
                .patternFilters(List.of(patFilter("host", "web-.*")))
                .build());

    assertFalse(matcher.matches("cpu.usage", Map.of("host", "db-01")));
  }

  @Test
  void andSemantics_multipleLabelValuesMustAllMatch() {
    var matcher =
        matcherFor(
            req()
                .valueFilters(List.of(filter("env", "prod"), filter("region", "us-east")))
                .build());

    assertTrue(matcher.matches("m", Map.of("env", "prod", "region", "us-east")));
    assertFalse(matcher.matches("m", Map.of("env", "prod", "region", "eu-west")));
    assertFalse(matcher.matches("m", Map.of("env", "dev", "region", "us-east")));
  }

  // --- no filters ---

  @Test
  void noFilters_matchesEverything() {
    var matcher = matcherFor(req().build());
    assertTrue(matcher.matches("any.metric", Map.of("env", "prod")));
    assertTrue(matcher.matches("another.metric", Map.of()));
  }

  // --- helpers ---

  private MetricPathMatcher matcherFor(SearchMetricsRequest request) {
    return MetricPathMatcher.fromRequest(request);
  }

  private SearchMetricsRequest.SearchMetricsRequestBuilder req() {
    return SearchMetricsRequest.builder().tsStartMillis(0).tsEndMillis(Long.MAX_VALUE);
  }

  private LabelValueFilter filter(String label, String value) {
    return LabelValueFilter.builder().label(label).value(value).build();
  }

  private LabelValuePatternFilter patFilter(String label, String pattern) {
    return LabelValuePatternFilter.builder().label(label).pattern(pattern).build();
  }
}

/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.metrics.ch;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.okapi.exceptions.BadRequestException;
import org.okapi.rest.search.AnyMetricOrValueFilter;

public class AnyMetricOrValueFilterMatcherTest {

  // --- null filter (always true) ---

  @Test
  void nullFilter_alwaysMatches() {
    var matcher = AnyMetricOrValueFilterMatcher.fromFilter(null);
    assertTrue(matcher.matches("cpu.usage", Map.of("env", "prod")));
    assertTrue(matcher.matches("any.metric", Map.of()));
  }

  // --- exact value on metric name ---

  @Test
  void exactValue_matchesMetricName() {
    var matcher = matcherFor(filter().value("cpu.usage").build());
    assertTrue(matcher.matches("cpu.usage", Map.of("env", "prod")));
  }

  @Test
  void exactValue_doesNotMatchMetricName() {
    var matcher = matcherFor(filter().value("cpu.usage").build());
    assertFalse(matcher.matches("mem.usage", Map.of()));
  }

  // --- exact value on tag value ---

  @Test
  void exactValue_matchesTagValue() {
    var matcher = matcherFor(filter().value("web-01").build());
    assertTrue(matcher.matches("cpu.usage", Map.of("host", "web-01", "env", "prod")));
  }

  @Test
  void exactValue_doesNotMatchTagValue() {
    var matcher = matcherFor(filter().value("web-01").build());
    assertFalse(matcher.matches("cpu.usage", Map.of("host", "db-01", "env", "prod")));
  }

  // --- RE2 pattern on metric name ---

  @Test
  void pattern_matchesMetricName() {
    var matcher = matcherFor(filter().pattern("cpu\\..*").build());
    assertTrue(matcher.matches("cpu.usage", Map.of()));
    assertTrue(matcher.matches("cpu.idle", Map.of()));
  }

  @Test
  void pattern_doesNotMatchMetricName() {
    var matcher = matcherFor(filter().pattern("cpu\\..*").build());
    assertFalse(matcher.matches("mem.usage", Map.of()));
  }

  // --- RE2 pattern on tag value ---

  @Test
  void pattern_matchesTagValue() {
    var matcher = matcherFor(filter().pattern("web-.*").build());
    assertTrue(matcher.matches("any.metric", Map.of("host", "web-01")));
  }

  @Test
  void pattern_doesNotMatchTagValue() {
    var matcher = matcherFor(filter().pattern("web-.*").build());
    assertFalse(matcher.matches("any.metric", Map.of("host", "db-01")));
  }

  // --- blank value falls through to pattern ---

  @Test
  void blankValue_consultsPattern() {
    var matcher = matcherFor(filter().value("").pattern("checkout\\..*").build());
    assertTrue(matcher.matches("checkout.http.requests", Map.of("env", "prod")));
    assertTrue(matcher.matches("checkout.jvm.heap", Map.of()));
    assertFalse(matcher.matches("payment.http.requests", Map.of("env", "prod")));
  }

  // --- value takes precedence over pattern ---

  @Test
  void value_takesPrecedenceOverPattern() {
    // value="cpu.usage" matches, pattern="no-match" would not — value wins
    var matcher = matcherFor(filter().value("cpu.usage").pattern("no-match").build());
    assertTrue(matcher.matches("cpu.usage", Map.of()));
    // value="cpu.usage" does not match "mem.usage", pattern is ignored
    assertFalse(matcher.matches("mem.usage", Map.of()));
  }

  // --- both blank → BadRequestException ---

  @Test
  void bothBlank_throwsBadRequest() {
    assertThrows(
        BadRequestException.class,
        () -> AnyMetricOrValueFilterMatcher.fromFilter(filter().build()));
  }

  @Test
  void emptyStrings_throwsBadRequest() {
    assertThrows(
        BadRequestException.class,
        () -> AnyMetricOrValueFilterMatcher.fromFilter(filter().value("").pattern("").build()));
  }

  // --- helpers ---

  private AnyMetricOrValueFilterMatcher matcherFor(AnyMetricOrValueFilter f) {
    return AnyMetricOrValueFilterMatcher.fromFilter(f);
  }

  private AnyMetricOrValueFilter.AnyMetricOrValueFilterBuilder filter() {
    return AnyMetricOrValueFilter.builder();
  }
}

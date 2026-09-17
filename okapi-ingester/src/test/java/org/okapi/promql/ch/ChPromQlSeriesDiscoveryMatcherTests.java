/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.ch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.okapi.promql.parse.LabelMatcher;
import org.okapi.promql.parse.LabelOp;

class ChPromQlSeriesDiscoveryMatcherTests {
  private static final Map<String, String> LABELS = Map.of("env", "prod");

  @Test
  void missingLabelsMatchAsEmptyStrings() {
    assertTrue(matches(LabelOp.EQ, ""));
    assertTrue(matches(LabelOp.NE, "west"));
    assertTrue(matches(LabelOp.RE, ".*"));
    assertTrue(matches(LabelOp.NRE, "west"));
  }

  @Test
  void missingLabelsStillRejectNonMatchingConditions() {
    assertFalse(matches(LabelOp.EQ, "west"));
    assertFalse(matches(LabelOp.NE, ""));
    assertFalse(matches(LabelOp.RE, ".+"));
    assertFalse(matches(LabelOp.NRE, ".*"));
  }

  @Test
  void discoveryLabelsCarryUnitAsInternalMetadata() {
    assertEquals(
        Map.of("env", "prod", "__unit__", "seconds"),
        ChPromQlSeriesDiscovery.labelsWithUnit(LABELS, "seconds"));
    assertEquals(
        Map.of("env", "prod", "__unit__", ""),
        ChPromQlSeriesDiscovery.labelsWithUnit(LABELS, null));
  }

  private static boolean matches(LabelOp op, String value) {
    return ChPromQlSeriesDiscovery.matches(LABELS, List.of(new LabelMatcher("zone", op, value)));
  }
}

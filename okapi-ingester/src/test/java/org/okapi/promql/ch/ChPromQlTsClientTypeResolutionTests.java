/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.ch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class ChPromQlTsClientTypeResolutionTests {
  @Test
  void aSingleDistinctTypeResolvesNormally() {
    assertEquals(
        ChPromQlTsClient.MetricEventType.SUM,
        ChPromQlTsClient.uniqueMetricEventType(List.of("SUM", "SUM")).orElseThrow());
  }

  @Test
  void conflictingTypesForOneIdentityAreRejected() {
    assertThrows(
        IllegalStateException.class,
        () -> ChPromQlTsClient.uniqueMetricEventType(List.of("GAUGE", "SUM")));
  }
}

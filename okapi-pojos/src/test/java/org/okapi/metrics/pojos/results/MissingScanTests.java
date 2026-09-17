/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.metrics.pojos.results;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MissingScanTests {
  @Test
  void missingScanIsExplicitlyEmpty() {
    assertTrue(MissingScan.INSTANCE.getTimestamps().isEmpty());
    assertTrue(MissingScan.INSTANCE.getValues().isEmpty());
  }
}

/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql;

import org.okapi.Statistics;
import org.okapi.promql.eval.ts.StatisticsMerger;

// Not used in the new Scan-based flow, but kept for constructor compatibility
public class MockStatsMerger implements StatisticsMerger {
  @Override
  public Statistics merge(Statistics a, Statistics b) {
    // Return the second for determinism; tests do not rely on this
    return b;
  }
}

/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.query;

import org.okapi.Statistics;
import org.okapi.promql.eval.ts.StatisticsMerger;

public class NoOpStatisticsMerger implements StatisticsMerger {
  @Override
  public Statistics merge(Statistics a, Statistics b) {
    return a != null ? a : b;
  }
}

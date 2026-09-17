/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.eval.ts;

import org.okapi.Statistics;

public interface StatisticsMerger {
  Statistics merge(Statistics a, Statistics b);
}

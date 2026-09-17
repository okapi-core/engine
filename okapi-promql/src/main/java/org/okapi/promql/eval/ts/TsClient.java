/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.eval.ts;

import java.util.Map;
import org.okapi.metrics.pojos.results.Scan;

public interface TsClient {
  // Returns a Scan representing the data for the matched time series over [startMs, endMs].
  // The concrete Scan may be GaugeScan, HistoScan, or SumScan.
  Scan get(String name, Map<String, String> tags, RESOLUTION res, long startMs, long endMs);
}

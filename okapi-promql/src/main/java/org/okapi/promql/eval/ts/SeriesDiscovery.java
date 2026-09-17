/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.eval.ts;

import java.util.List;
import org.okapi.promql.eval.VectorData.SeriesId;
import org.okapi.promql.parse.*;

public interface SeriesDiscovery {
  /** Expand a selector into concrete (metric, tags) series. */
  List<SeriesId> expand(String metricOrNull, List<LabelMatcher> matchers, long start, long end);
}

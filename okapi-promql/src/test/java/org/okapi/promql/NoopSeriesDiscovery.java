/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql;

import java.util.List;
import org.okapi.promql.eval.VectorData.*;
import org.okapi.promql.eval.ts.SeriesDiscovery;
import org.okapi.promql.parse.LabelMatcher;

public class NoopSeriesDiscovery implements SeriesDiscovery {
  @Override
  public List<SeriesId> expand(
      String metricOrNull, List<LabelMatcher> matchers, long st, long end) {
    // For scalar-only sanity tests, we won’t reach here.
    return List.of();
  }
}

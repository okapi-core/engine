/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.testing;

import java.util.List;
import org.okapi.promql.testing.PromQlTestAst.LoadCmd;

public interface PromQlTestIngestor {
  void clear();

  void ingestLoad(long startMs, LoadCmd loadCmd);

  List<IngestedSeries> series();

  record IngestedSeries(
      String metric,
      java.util.Map<String, String> labels,
      long startMs,
      long stepMs,
      boolean withNhcb,
      TestMetricClassifier.MetricType metricType,
      java.util.List<org.okapi.promql.testing.PromQlTestAst.PointExpr> points) {}
}

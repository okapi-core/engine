/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.testing;

import org.okapi.promql.testing.PromQlTestAst.LoadCmd;
import org.okapi.promql.testing.PromQlTestAst.SeriesDef;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class InMemoryPromQlTestIngestor implements PromQlTestIngestor {
  private final List<IngestedSeries> series = new ArrayList<>();
  private final TestMetricClassifier classifier = new TestMetricClassifier();

  @Override
  public void clear() {
    series.clear();
  }

  @Override
  public void ingestLoad(long startMs, LoadCmd loadCmd) {
    long stepMs = DurationParser.toMillis(loadCmd.step().text());
    var histogramBases = classifier.collectHistogramBases(loadCmd.series());
    for (SeriesDef def : loadCmd.series()) {
      String metric = def.metric();
      Map<String, String> labels = def.labels();
      TestMetricClassifier.MetricType metricType =
          classifier.classify(def, loadCmd.withNhcb(), histogramBases);
      series.add(
          new IngestedSeries(
              metric, labels, startMs, stepMs, loadCmd.withNhcb(), metricType, def.points()));
    }
  }

  @Override
  public List<IngestedSeries> series() {
    return List.copyOf(series);
  }

}

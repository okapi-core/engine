/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.testing;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.okapi.promql.testing.PromQlTestAst.LoadCmd;
import org.okapi.promql.testing.PromQlTestAst.SeriesDef;

public final class InMemoryPromQlTestIngestor implements PromQlTestIngestor {
  private final List<IngestedSeries> series = new ArrayList<>();
  private final TestMetricClassifier classifier = new TestMetricClassifier();

  @Override
  public void clear() {
    series.clear();
  }

  @Override
  public void ingestLoad(long startMs, LoadCmd loadCmd) {
    long stepMs = parseDurationToMillis(loadCmd.step().text());
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

  private long parseDurationToMillis(String duration) {
    long total = 0L;
    int i = 0;
    while (i < duration.length()) {
      int start = i;
      while (i < duration.length()
          && (Character.isDigit(duration.charAt(i)) || duration.charAt(i) == '.')) {
        i++;
      }
      if (start == i) {
        throw new IllegalArgumentException("invalid duration: " + duration);
      }
      double value = Double.parseDouble(duration.substring(start, i));
      if (i >= duration.length()) {
        throw new IllegalArgumentException("invalid duration: " + duration);
      }
      if (duration.startsWith("ms", i)) {
        total += Math.round(value);
        i += 2;
        continue;
      }
      char unit = duration.charAt(i++);
      total += Math.round(value * unitMultiplier(unit));
    }
    return total;
  }

  private long unitMultiplier(char unit) {
    return switch (unit) {
      case 's' -> 1000L;
      case 'm' -> 60_000L;
      case 'h' -> 3_600_000L;
      case 'd' -> 86_400_000L;
      case 'w' -> 604_800_000L;
      case 'y' -> 31_536_000_000L;
      default -> throw new IllegalArgumentException("unsupported duration unit: " + unit);
    };
  }
}

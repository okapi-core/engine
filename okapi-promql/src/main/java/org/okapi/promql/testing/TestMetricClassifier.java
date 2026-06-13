/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.testing;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.okapi.promql.testing.PromQlTestAst.PointExpr;
import org.okapi.promql.testing.PromQlTestAst.SeriesDef;

public final class TestMetricClassifier {
  public enum MetricType {
    GAUGE,
    COUNTER,
    HISTOGRAM
  }

  public MetricType classify(SeriesDef series, boolean loadWithNhcb, Set<String> histogramBases) {
    String metric = series.metric();
    Map<String, String> labels = series.labels();

    if (loadWithNhcb || hasHistogramPoint(series.points()) || labels.containsKey("le")) {
      return MetricType.HISTOGRAM;
    }

    if (metric != null) {
      if (metric.endsWith("_total") || metric.endsWith("_counter")) {
        return MetricType.COUNTER;
      }
      if ((metric.endsWith("_sum") || metric.endsWith("_count"))
          && histogramBases.contains(histogramBase(metric))) {
        return MetricType.COUNTER;
      }
    }

    return MetricType.GAUGE;
  }

  public Set<String> collectHistogramBases(List<SeriesDef> series) {
    Set<String> bases = new HashSet<>();
    for (SeriesDef def : series) {
      if (def.labels().containsKey("le")) {
        String name = def.metric();
        if (name != null && name.endsWith("_bucket")) {
          bases.add(name.substring(0, name.length() - "_bucket".length()));
        } else if (name != null) {
          bases.add(name);
        }
      }
    }
    return bases;
  }

  private boolean hasHistogramPoint(List<PointExpr> points) {
    for (PointExpr point : points) {
      if (point instanceof PromQlTestAst.HistogramPoint) {
        return true;
      }
      if (point instanceof PromQlTestAst.RepeatPoint repeat
          && repeat.value() instanceof PromQlTestAst.HistogramPoint) {
        return true;
      }
      if (point instanceof PromQlTestAst.StepSequencePoint step) {
        if (step.start() instanceof PromQlTestAst.HistogramPoint
            || step.step() instanceof PromQlTestAst.HistogramPoint) {
          return true;
        }
      }
    }
    return false;
  }

  private String histogramBase(String metric) {
    if (metric.endsWith("_sum")) {
      return metric.substring(0, metric.length() - "_sum".length());
    }
    if (metric.endsWith("_count")) {
      return metric.substring(0, metric.length() - "_count".length());
    }
    return metric;
  }
}

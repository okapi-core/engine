/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.testing;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.okapi.promql.testing.PromQlTestAst.*;

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
    if (loadCmd.withNhcb()) ingestNativeCustomBucketHistograms(startMs, stepMs, loadCmd.series());
  }

  @Override
  public List<IngestedSeries> series() {
    return List.copyOf(series);
  }

  private void ingestNativeCustomBucketHistograms(
      long startMs, long stepMs, List<SeriesDef> definitions) {
    Map<HistogramKey, List<SeriesDef>> buckets = new LinkedHashMap<>();
    Map<HistogramKey, SeriesDef> sums = new HashMap<>();
    for (SeriesDef definition : definitions) {
      String metric = definition.metric();
      if (metric == null) continue;
      if (metric.endsWith("_bucket") && definition.labels().containsKey("le")) {
        var labels = new HashMap<>(definition.labels());
        labels.remove("le");
        buckets
            .computeIfAbsent(
                new HistogramKey(metric.substring(0, metric.length() - "_bucket".length()), labels),
                ignored -> new ArrayList<>())
            .add(definition);
      } else if (metric.endsWith("_sum")) {
        sums.put(
            new HistogramKey(
                metric.substring(0, metric.length() - "_sum".length()), definition.labels()),
            definition);
      }
    }

    for (var entry : buckets.entrySet()) {
      var orderedBuckets = new ArrayList<>(entry.getValue());
      orderedBuckets.sort(
          Comparator.comparingDouble(bucket -> parseBound(bucket.labels().get("le"))));
      List<List<PointExpr>> expandedBuckets =
          orderedBuckets.stream().map(bucket -> expandPoints(bucket.points())).toList();
      List<PointExpr> expandedSum =
          sums.containsKey(entry.getKey())
              ? expandPoints(sums.get(entry.getKey()).points())
              : List.of();
      int pointCount = expandedBuckets.stream().mapToInt(List::size).max().orElse(0);
      List<PointExpr> nativePoints = new ArrayList<>(pointCount);
      for (int i = 0; i < pointCount; i++) {
        nativePoints.add(nativeHistogramPoint(orderedBuckets, expandedBuckets, expandedSum, i));
      }
      series.add(
          new IngestedSeries(
              entry.getKey().metric(),
              entry.getKey().labels(),
              startMs,
              stepMs,
              true,
              TestMetricClassifier.MetricType.HISTOGRAM,
              nativePoints));
    }
  }

  private PointExpr nativeHistogramPoint(
      List<SeriesDef> buckets,
      List<List<PointExpr>> expandedBuckets,
      List<PointExpr> expandedSum,
      int index) {
    List<Double> cumulative = new ArrayList<>(buckets.size());
    for (List<PointExpr> bucket : expandedBuckets) {
      if (index >= bucket.size() || !(bucket.get(index) instanceof NumberPoint number))
        return new MissingPoint();
      cumulative.add(number.value());
    }
    List<Double> bucketCounts = new ArrayList<>(cumulative.size());
    double previous = 0d;
    for (double count : cumulative) {
      bucketCounts.add(count - previous);
      previous = count;
    }
    List<Double> customValues = new ArrayList<>();
    for (SeriesDef bucket : buckets) {
      double bound = parseBound(bucket.labels().get("le"));
      if (!Double.isInfinite(bound)) customValues.add(bound);
    }
    double sum =
        index < expandedSum.size() && expandedSum.get(index) instanceof NumberPoint number
            ? number.value()
            : 0d;
    Map<String, HistogramValue> fields = new HashMap<>();
    fields.put("schema", new HistogramNumber(-53));
    fields.put("sum", new HistogramNumber(sum));
    fields.put("count", new HistogramNumber(cumulative.get(cumulative.size() - 1)));
    fields.put("buckets", new HistogramNumberList(bucketCounts));
    fields.put("custom_values", new HistogramNumberList(customValues));
    return new HistogramPoint(new HistogramLiteral(fields));
  }

  private List<PointExpr> expandPoints(List<PointExpr> points) {
    List<PointExpr> expanded = new ArrayList<>();
    for (PointExpr point : points) {
      switch (point) {
        case RepeatPoint repeat -> {
          for (int i = 0; i <= repeat.count(); i++) expanded.add(repeat.value());
        }
        case StepSequencePoint sequence
            when sequence.start() instanceof NumberPoint start
                && sequence.step() instanceof NumberPoint step -> {
          for (int i = 0; i <= sequence.count(); i++)
            expanded.add(new NumberPoint(start.value() + step.value() * i));
        }
        default -> expanded.add(point);
      }
    }
    return expanded;
  }

  private double parseBound(String bound) {
    if (bound.equalsIgnoreCase("+Inf") || bound.equalsIgnoreCase("Inf"))
      return Double.POSITIVE_INFINITY;
    return Double.parseDouble(bound);
  }

  private record HistogramKey(String metric, Map<String, String> labels) {}
}

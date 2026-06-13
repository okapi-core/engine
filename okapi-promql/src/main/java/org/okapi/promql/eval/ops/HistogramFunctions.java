/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.eval.ops;

import java.util.*;
import org.okapi.metrics.pojos.results.HistoScan;
import org.okapi.metrics.pojos.results.HistoScanMerger;
import org.okapi.promql.eval.*;
import org.okapi.promql.eval.HistogramSeries;
import org.okapi.promql.eval.VectorData;
import org.okapi.promql.eval.VectorData.*;

/** Pure functions over histogram range vectors. */
public final class HistogramFunctions {
  private HistogramFunctions() {}

  public static InstantVectorResult count(InstantVectorResult vector) {
    return mapHistograms(vector, HistogramSeries.HistogramSample::count);
  }

  public static InstantVectorResult sum(InstantVectorResult vector) {
    return mapHistograms(vector, HistogramSeries.HistogramSample::sum);
  }

  public static InstantVectorResult avg(InstantVectorResult vector) {
    return mapHistograms(vector, histogram -> histogram.sum() / histogram.count());
  }

  public static InstantVectorResult stddev(InstantVectorResult vector) {
    return mapHistograms(vector, histogram -> Math.sqrt(variance(histogram)));
  }

  public static InstantVectorResult stdvar(InstantVectorResult vector) {
    return mapHistograms(vector, HistogramFunctions::variance);
  }

  public static InstantVectorResult fraction(
      double lower, double upper, InstantVectorResult vector) {
    List<SeriesSample> out = new ArrayList<>();
    Map<ClassicBucketKey, List<ClassicBucket>> classicBuckets = new LinkedHashMap<>();
    for (var seriesSample : vector.data()) {
      if (seriesSample.sample().isHistogram()) {
        var histogram = seriesSample.sample().histogram();
        if (!(histogram instanceof HistogramSeries.NativeHistogramSample nativeHistogram)) continue;
        out.add(
            new SeriesSample(
                SeriesIds.derived(seriesSample.series()),
                new Sample(
                    seriesSample.sample().ts(),
                    seriesSample.sample().sourceTs(),
                    fraction(lower, upper, nativeHistogram))));
        continue;
      }
      String bound = seriesSample.series().labels().tags().get("le");
      if (bound == null) continue;
      Map<String, String> labels = new HashMap<>(seriesSample.series().labels().tags());
      labels.remove("le");
      var key = new ClassicBucketKey(labels, seriesSample.sample().ts());
      classicBuckets
          .computeIfAbsent(key, ignored -> new ArrayList<>())
          .add(new ClassicBucket(parseBound(bound), seriesSample.sample().value()));
    }
    for (var entry : classicBuckets.entrySet()) {
      var buckets = entry.getValue();
      buckets.sort(Comparator.comparingDouble(ClassicBucket::upperBound));
      double[] bounds = buckets.stream().mapToDouble(ClassicBucket::upperBound).toArray();
      double[] cumulative = buckets.stream().mapToDouble(ClassicBucket::cumulativeCount).toArray();
      out.add(
          new SeriesSample(
              new SeriesId("", new Labels(entry.getKey().labels())),
              new Sample(entry.getKey().timestamp(), fraction(lower, upper, bounds, cumulative))));
    }
    return new InstantVectorResult(out);
  }

  public static InstantVectorResult quantile(double quantile, InstantVectorResult vector) {
    List<SeriesSample> out = new ArrayList<>();
    Map<ClassicBucketKey, List<ClassicBucket>> classicBuckets = new LinkedHashMap<>();
    for (var seriesSample : vector.data()) {
      if (seriesSample.sample().isHistogram()) {
        var histogram = seriesSample.sample().histogram();
        if (!(histogram instanceof HistogramSeries.NativeHistogramSample nativeHistogram)) continue;
        out.add(
            new SeriesSample(
                SeriesIds.derived(seriesSample.series()),
                new Sample(
                    seriesSample.sample().ts(),
                    seriesSample.sample().sourceTs(),
                    quantile(quantile, nativeHistogram))));
        continue;
      }
      String bound = seriesSample.series().labels().tags().get("le");
      if (bound == null) continue;
      Map<String, String> labels = new HashMap<>(seriesSample.series().labels().tags());
      labels.remove("le");
      var key = new ClassicBucketKey(labels, seriesSample.sample().ts());
      classicBuckets
          .computeIfAbsent(key, ignored -> new ArrayList<>())
          .add(new ClassicBucket(parseBound(bound), seriesSample.sample().value()));
    }
    for (var entry : classicBuckets.entrySet()) {
      var buckets = entry.getValue();
      buckets.sort(Comparator.comparingDouble(ClassicBucket::upperBound));
      out.add(
          new SeriesSample(
              new SeriesId("", new Labels(entry.getKey().labels())),
              new Sample(
                  entry.getKey().timestamp(),
                  quantile(
                      quantile,
                      buckets.stream().mapToDouble(ClassicBucket::upperBound).toArray(),
                      buckets.stream().mapToDouble(ClassicBucket::cumulativeCount).toArray()))));
    }
    return new InstantVectorResult(out);
  }

  public static InstantVectorResult quantiles(
      InstantVectorResult vector, String label, List<Double> quantiles) {
    List<SeriesSample> out = new ArrayList<>();
    for (double quantile : quantiles) {
      for (var seriesSample : quantile(quantile, vector).data()) {
        Map<String, String> labels = new HashMap<>(seriesSample.series().labels().tags());
        labels.put(label, Double.toString(quantile));
        out.add(
            new SeriesSample(
                new SeriesId("", new Labels(labels)),
                seriesSample.sample()));
      }
    }
    return new InstantVectorResult(out);
  }

  private static InstantVectorResult mapHistograms(
      InstantVectorResult vector,
      java.util.function.ToDoubleFunction<HistogramSeries.HistogramSample> function) {
    List<SeriesSample> out = new ArrayList<>();
    for (var seriesSample : vector.data()) {
      if (!seriesSample.sample().isHistogram()) continue;
      out.add(
          new SeriesSample(
              SeriesIds.derived(seriesSample.series()),
              new Sample(
                  seriesSample.sample().ts(),
                  seriesSample.sample().sourceTs(),
                  function.applyAsDouble(seriesSample.sample().histogram()))));
    }
    return new InstantVectorResult(out);
  }

  private static double variance(HistogramSeries.HistogramSample histogram) {
    if (!(histogram instanceof HistogramSeries.NativeHistogramSample nativeHistogram))
      return Double.NaN;
    double count = nativeHistogram.count();
    if (count == 0) return Double.NaN;
    double mean = nativeHistogram.sum() / count;
    double variance = nativeHistogram.zeroCount() * mean * mean;
    if (nativeHistogram.customValues().length > 0) {
      variance += customBucketVariance(
          nativeHistogram.customValues(), nativeHistogram.positiveBuckets(), mean);
    } else {
      variance += exponentialBucketVariance(nativeHistogram, mean);
    }
    return variance / count;
  }

  private static double fraction(
      double lower, double upper, HistogramSeries.NativeHistogramSample histogram) {
    double[] customValues = histogram.customValues();
    double[] bucketCounts = histogram.positiveBuckets();
    if (customValues.length == 0) return Double.NaN;
    double[] bounds = Arrays.copyOf(customValues, customValues.length + 1);
    bounds[bounds.length - 1] = Double.POSITIVE_INFINITY;
    double[] cumulative = new double[bucketCounts.length];
    double count = 0d;
    for (int i = 0; i < bucketCounts.length; i++) {
      count += bucketCounts[i];
      cumulative[i] = count;
    }
    return fraction(lower, upper, bounds, cumulative);
  }

  private static double quantile(
      double quantile, HistogramSeries.NativeHistogramSample histogram) {
    double[] customValues = histogram.customValues();
    double[] bucketCounts = histogram.positiveBuckets();
    if (customValues.length == 0) return Double.NaN;
    double[] bounds = Arrays.copyOf(customValues, customValues.length + 1);
    bounds[bounds.length - 1] = Double.POSITIVE_INFINITY;
    double[] cumulative = new double[bucketCounts.length];
    double count = 0d;
    for (int i = 0; i < bucketCounts.length; i++) {
      count += bucketCounts[i];
      cumulative[i] = count;
    }
    return quantile(quantile, bounds, cumulative);
  }

  private static double quantile(double quantile, double[] bounds, double[] cumulative) {
    if (Double.isNaN(quantile)) return Double.NaN;
    if (quantile < 0d) return Double.NEGATIVE_INFINITY;
    if (quantile > 1d) return Double.POSITIVE_INFINITY;
    if (cumulative.length == 0 || cumulative[cumulative.length - 1] == 0d) return Double.NaN;
    double rank = quantile * cumulative[cumulative.length - 1];
    double previousCount = 0d;
    double lowerBound = bounds.length > 0 && bounds[0] > 0d ? 0d : Double.NEGATIVE_INFINITY;
    for (int i = 0; i < cumulative.length; i++) {
      double upperBound = i < bounds.length ? bounds[i] : Double.POSITIVE_INFINITY;
      if (rank <= cumulative[i]) {
        if (Double.isInfinite(lowerBound)) return upperBound;
        if (Double.isInfinite(upperBound)) return lowerBound;
        double bucketCount = cumulative[i] - previousCount;
        if (bucketCount == 0d) return upperBound;
        return lowerBound + (upperBound - lowerBound) * (rank - previousCount) / bucketCount;
      }
      previousCount = cumulative[i];
      lowerBound = upperBound;
    }
    return bounds[bounds.length - 1];
  }

  private static double fraction(double lower, double upper, double[] bounds, double[] cumulative) {
    if (Double.isNaN(lower) || Double.isNaN(upper)) return Double.NaN;
    if (lower >= upper || cumulative.length == 0) return 0d;
    double total = cumulative[cumulative.length - 1];
    if (total == 0d) return Double.NaN;
    return (cumulativeAt(upper, bounds, cumulative) - cumulativeAt(lower, bounds, cumulative)) / total;
  }

  private static double cumulativeAt(double value, double[] bounds, double[] cumulative) {
    if (value == Double.NEGATIVE_INFINITY) return 0d;
    if (value == Double.POSITIVE_INFINITY) return cumulative[cumulative.length - 1];
    double previousCount = 0d;
    double lowerBound = bounds.length > 0 && bounds[0] > 0d ? 0d : Double.NEGATIVE_INFINITY;
    for (int i = 0; i < cumulative.length; i++) {
      double upperBound = i < bounds.length ? bounds[i] : Double.POSITIVE_INFINITY;
      if (value <= upperBound) {
        if (Double.isInfinite(lowerBound)) return value < upperBound ? 0d : cumulative[i];
        if (Double.isInfinite(upperBound)) return previousCount;
        double position = (value - lowerBound) / (upperBound - lowerBound);
        position = Math.max(0d, Math.min(1d, position));
        return previousCount + position * (cumulative[i] - previousCount);
      }
      previousCount = cumulative[i];
      lowerBound = upperBound;
    }
    return previousCount;
  }

  private static double parseBound(String value) {
    if (value.equalsIgnoreCase("+Inf") || value.equalsIgnoreCase("Inf"))
      return Double.POSITIVE_INFINITY;
    if (value.equalsIgnoreCase("-Inf")) return Double.NEGATIVE_INFINITY;
    return Double.parseDouble(value);
  }

  private record ClassicBucketKey(Map<String, String> labels, long timestamp) {}

  private record ClassicBucket(double upperBound, double cumulativeCount) {}

  private static double customBucketVariance(double[] bounds, double[] counts, double mean) {
    double variance = 0d;
    for (int i = 0; i < counts.length; i++) {
      double representative;
      if (i == 0) representative = bounds.length == 0 ? 0d : bounds[0];
      else if (i >= bounds.length) representative = bounds[bounds.length - 1];
      else representative = (bounds[i - 1] + bounds[i]) / 2d;
      double difference = representative - mean;
      variance += counts[i] * difference * difference;
    }
    return variance;
  }

  private static double exponentialBucketVariance(
      HistogramSeries.NativeHistogramSample histogram, double mean) {
    double variance = 0d;
    double base = Math.pow(2d, Math.pow(2d, -histogram.schema()));
    for (int i = 0; i < histogram.positiveBuckets().length; i++) {
      int bucket = histogram.positiveOffset() + i;
      double representative = Math.pow(base, bucket - 0.5d);
      double difference = representative - mean;
      variance += histogram.positiveBuckets()[i] * difference * difference;
    }
    for (int i = 0; i < histogram.negativeBuckets().length; i++) {
      int bucket = histogram.negativeOffset() + i;
      double representative = -Math.pow(base, bucket - 0.5d);
      double difference = representative - mean;
      variance += histogram.negativeBuckets()[i] * difference * difference;
    }
    return variance;
  }

  public static InstantVectorResult quantile(
      float q, RangeVectorResult rv, long rangeMs, EvalContext ctx) {
    List<SeriesSample> out = new ArrayList<>();

    // Group windows by label set (minus 'instance') to merge across instances
    var grouped = new LinkedHashMap<Map<String, String>, List<SeriesWindow>>();
    for (SeriesWindow w : rv.data()) {
      Map<String, String> key = new HashMap<>(w.id().labels().tags());
      key.remove("instance");
      grouped.computeIfAbsent(Collections.unmodifiableMap(key), k -> new ArrayList<>()).add(w);
    }

    for (var e : grouped.entrySet()) {
      VectorData.SeriesId rep = null;
      for (var w : e.getValue()) {
        if (rep == null) rep = new SeriesId(w.id().metric(), new Labels(e.getKey()));
      }
      if (rep == null) continue;

      for (long t = ctx.startMs; t <= ctx.endMs; t += ctx.stepMs) {
        long winStart = t - rangeMs;
        List<HistoScan> toMerge = new ArrayList<>();
        for (var w : e.getValue()) {
          if (w.scan() instanceof HistogramSeries hs) {
            collectPoints(hs, winStart, t, toMerge);
          } else if (w.scan() instanceof HistoScan hs) {
            if (overlaps(hs.getStart(), hs.getEnd(), winStart, t)) toMerge.add(hs);
          }
        }
        if (toMerge.isEmpty()) continue;
        HistoScan merged = HistoScanMerger.merge("", toMerge);
        out.add(new SeriesSample(rep, new Sample(t, quantileFromHistogram(q, merged))));
      }
    }
    return new InstantVectorResult(out);
  }

  static float quantileFromHistogram(double q, HistoScan hs) {
    List<Float> ubs = hs.getUbs();
    List<Integer> counts = hs.getCounts();
    if (counts == null || counts.isEmpty()) return Float.NaN;

    long total = 0;
    for (int c : counts) total += c;
    if (total <= 0) return Float.NaN;

    double target = q * total;
    long cum = 0;
    int k = -1;
    for (int i = 0; i < counts.size(); i++) {
      long next = cum + counts.get(i);
      if (target <= next) { k = i; break; }
      cum = next;
    }
    if (k == -1) k = counts.size() - 1;

    int n = ubs.size();
    float lower, upper;
    if (k == 0) {
      lower = Float.NEGATIVE_INFINITY; upper = ubs.get(0);
    } else if (k < n) {
      lower = ubs.get(k - 1); upper = ubs.get(k);
    } else {
      lower = (n >= 1) ? ubs.get(n - 1) : Float.NEGATIVE_INFINITY;
      upper = Float.POSITIVE_INFINITY;
    }

    int inBucket = counts.get(k);
    if (inBucket <= 0) return Float.isInfinite(upper) ? lower : upper;

    double pos = (target - cum) / Math.max(inBucket, 1);
    if (Float.isInfinite(lower)) return upper;
    if (Float.isInfinite(upper)) return lower;
    return (float) (lower + pos * (upper - lower));
  }

  private static void collectPoints(
      HistogramSeries hs, long winStart, long winEnd, List<HistoScan> out) {
    for (var p : hs.getPoints()) {
      if (!overlaps(p.startMs(), p.endMs(), winStart, winEnd)) continue;
      if (!(p instanceof HistogramSeries.ExplicitHistogramSample explicit)) continue;
      float[] bounds = explicit.upperBounds();
      int[] counts = explicit.counts();
      List<Float> ubs = new ArrayList<>(bounds == null ? 0 : bounds.length);
      if (bounds != null) for (float b : bounds) ubs.add(b);
      List<Integer> cs = new ArrayList<>(counts == null ? 0 : counts.length);
      if (counts != null) for (int c : counts) cs.add(c);
      out.add(new HistoScan("", explicit.startMs(), explicit.endMs(), ubs, cs));
    }
  }

  private static boolean overlaps(long startMs, long endMs, long winStart, long winEnd) {
    return startMs <= winEnd && endMs >= winStart;
  }
}

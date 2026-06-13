/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.eval.ops;

import java.util.*;
import java.util.function.ToDoubleFunction;
import org.okapi.metrics.pojos.results.HistoScan;
import org.okapi.metrics.pojos.results.HistoScanMerger;
import org.okapi.promql.eval.*;
import org.okapi.promql.eval.HistogramSeries;
import org.okapi.promql.eval.VectorData;
import org.okapi.promql.eval.VectorData.*;
import org.okapi.promql.eval.exceptions.EvaluationException;

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

  public static InstantVectorResult trim(
      InstantVectorResult vector, double cutoff, boolean keepLower) {
    List<SeriesSample> out = new ArrayList<>();
    for (var seriesSample : vector.data()) {
      if (!(seriesSample.sample().histogram()
          instanceof HistogramSeries.NativeHistogramSample histogram)) continue;
      out.add(
          new SeriesSample(
              seriesSample.series(),
              new Sample(
                  seriesSample.sample().ts(),
                  seriesSample.sample().sourceTs(),
                  trim(histogram, cutoff, keepLower))));
    }
    return new InstantVectorResult(out);
  }

  public static InstantVectorResult fraction(
      double lower, double upper, InstantVectorResult vector) {
    return mapHistogramBuckets(
        vector,
        histogram -> fraction(lower, upper, histogram),
        buckets -> fraction(lower, upper, buckets.bounds(), buckets.cumulative()));
  }

  public static InstantVectorResult quantile(double quantile, InstantVectorResult vector) {
    return mapHistogramBuckets(
        vector,
        histogram -> quantile(quantile, histogram),
        buckets -> quantile(quantile, buckets.bounds(), buckets.cumulative()));
  }

  private static InstantVectorResult mapHistogramBuckets(
      InstantVectorResult vector,
      ToDoubleFunction<HistogramSeries.NativeHistogramSample> nativeFunction,
      ToDoubleFunction<CumulativeBuckets> classicFunction) {
    List<SeriesSample> out = new ArrayList<>();
    Map<ClassicBucketKey, NativeHistogram> nativeHistograms = new LinkedHashMap<>();
    Map<ClassicBucketKey, ClassicHistogram> classicHistograms = new LinkedHashMap<>();
    for (var seriesSample : vector.data()) {
      if (seriesSample.sample().isHistogram()) {
        var histogram = seriesSample.sample().histogram();
        if (!(histogram instanceof HistogramSeries.NativeHistogramSample nativeHistogram)) continue;
        var key = new ClassicBucketKey(outputLabels(seriesSample.series(), false), seriesSample.sample().ts());
        var previous =
            nativeHistograms.putIfAbsent(
                key, new NativeHistogram(seriesSample, nativeHistogram));
        if (previous != null && !previous.sample().series().metric().equals(seriesSample.series().metric())) {
          throw ambiguousHistogram();
        }
        continue;
      }
      String bound = seriesSample.series().labels().tags().get("le");
      if (bound == null) continue;
      Double upperBound = parseBound(bound);
      if (upperBound == null) continue;
      var key = new ClassicBucketKey(outputLabels(seriesSample.series(), true), seriesSample.sample().ts());
      var classic =
          classicHistograms.computeIfAbsent(key, ignored -> new ClassicHistogram());
      classic.metrics().add(seriesSample.series().metric());
      classic.buckets().add(new ClassicBucket(upperBound, seriesSample.sample().value()));
    }
    for (var entry : nativeHistograms.entrySet()) {
      var classic = classicHistograms.get(entry.getKey());
      if (classic != null) {
        if (classic.metrics().size() == 1
            && classic.metrics().contains(entry.getValue().sample().series().metric())) continue;
        throw ambiguousHistogram();
      }
      var seriesSample = entry.getValue().sample();
      out.add(
          new SeriesSample(
              SeriesIds.derived(seriesSample.series()),
              new Sample(
                  seriesSample.sample().ts(),
                  seriesSample.sample().sourceTs(),
                  nativeFunction.applyAsDouble(entry.getValue().histogram()))));
    }
    for (var entry : classicHistograms.entrySet()) {
      var nativeHistogram = nativeHistograms.get(entry.getKey());
      if (nativeHistogram != null) {
        if (entry.getValue().metrics().size() == 1
            && entry.getValue().metrics().contains(nativeHistogram.sample().series().metric())) continue;
        throw ambiguousHistogram();
      }
      if (entry.getValue().metrics().size() != 1) throw ambiguousHistogram();
      out.add(
          new SeriesSample(
              new SeriesId("", new Labels(entry.getKey().labels())),
              new Sample(entry.getKey().timestamp(), classicFunction.applyAsDouble(normalize(entry.getValue().buckets())))));
    }
    return new InstantVectorResult(out);
  }

  private static Map<String, String> outputLabels(SeriesId series, boolean removeBound) {
    Map<String, String> labels = new HashMap<>(series.labels().tags());
    if (removeBound) labels.remove("le");
    labels.remove("__name__");
    labels.remove("__type__");
    labels.remove("__unit__");
    return labels;
  }

  private static EvaluationException ambiguousHistogram() {
    return new EvaluationException("vector cannot contain metrics with the same labelset");
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
    if (!Double.isFinite(nativeHistogram.sum())) return nativeHistogram.sum();
    double count = nativeHistogram.count();
    if (count == 0) return Double.NaN;
    double mean = nativeHistogram.sum() / count;
    double variance = nativeHistogram.zeroCount() * mean * mean;
    if (hasCustomBuckets(nativeHistogram)) {
      variance += customBucketVariance(
          nativeHistogram.customValues(), nativeHistogram.positiveBuckets(), mean);
    } else {
      variance += exponentialBucketVariance(nativeHistogram, mean);
    }
    return variance / count;
  }

  private static double fraction(
      double lower, double upper, HistogramSeries.NativeHistogramSample histogram) {
    if (!hasCustomBuckets(histogram)) {
      if (Double.isNaN(lower) || Double.isNaN(upper)) return Double.NaN;
      if (lower >= upper) return 0d;
      if (histogram.count() == 0d) return Double.NaN;
      double included = 0d;
      for (var bucket : exponentialBuckets(histogram)) {
        included += overlap(bucket, lower, upper);
      }
      return included / histogram.count();
    }
    double[] customValues = histogram.customValues();
    double[] bucketCounts = histogram.positiveBuckets();
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
    if (!hasCustomBuckets(histogram)) {
      if (Double.isNaN(quantile)) return Double.NaN;
      if (quantile < 0d) return Double.NEGATIVE_INFINITY;
      if (quantile > 1d) return Double.POSITIVE_INFINITY;
      if (histogram.count() == 0d) return Double.NaN;
      double rank = quantile * histogram.count();
      double cumulative = 0d;
      List<NativeBucket> buckets = exponentialBuckets(histogram);
      for (int i = 0; i < buckets.size(); i++) {
        var bucket = buckets.get(i);
        if (bucket.count() == 0d) continue;
        if (rank < cumulative + bucket.count() || rank == 0d) {
          return interpolate(bucket, (rank - cumulative) / bucket.count());
        }
        cumulative += bucket.count();
        if (rank == cumulative) {
          for (int j = i + 1; j < buckets.size(); j++) {
            if (buckets.get(j).count() != 0d) return buckets.get(j).lower();
          }
          return bucket.upper();
        }
      }
      return Double.NaN;
    }
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

  private static List<NativeBucket> exponentialBuckets(
      HistogramSeries.NativeHistogramSample histogram) {
    List<NativeBucket> buckets = new ArrayList<>();
    double base = Math.pow(2d, Math.pow(2d, -histogram.schema()));
    for (int i = histogram.negativeBuckets().length - 1; i >= 0; i--) {
      int index = histogram.negativeOffset() + i;
      double upper = -Math.pow(base, index - 1d);
      double lower = -Math.pow(base, index);
      buckets.add(new NativeBucket(lower, upper, histogram.negativeBuckets()[i], true));
    }
    if (histogram.zeroCount() != 0d) {
      boolean hasNegative = Arrays.stream(histogram.negativeBuckets()).anyMatch(count -> count != 0d);
      boolean hasPositive = Arrays.stream(histogram.positiveBuckets()).anyMatch(count -> count != 0d);
      buckets.add(
          new NativeBucket(
              hasNegative || !hasPositive ? -histogram.zeroThreshold() : 0d,
              hasPositive || !hasNegative ? histogram.zeroThreshold() : 0d,
              histogram.zeroCount(),
              false));
    }
    for (int i = 0; i < histogram.positiveBuckets().length; i++) {
      int index = histogram.positiveOffset() + i;
      double lower = Math.pow(base, index - 1d);
      double upper = Math.pow(base, index);
      buckets.add(new NativeBucket(lower, upper, histogram.positiveBuckets()[i], true));
    }
    return buckets;
  }

  private static boolean hasCustomBuckets(HistogramSeries.NativeHistogramSample histogram) {
    return histogram.schema() == HistogramSeries.CUSTOM_BUCKET_SCHEMA;
  }

  private static HistogramSeries.NativeHistogramSample trim(
      HistogramSeries.NativeHistogramSample histogram, double cutoff, boolean keepLower) {
    if (keepLower && cutoff == Double.NEGATIVE_INFINITY
        || !keepLower && cutoff == Double.POSITIVE_INFINITY) return histogram;
    if (hasCustomBuckets(histogram)) return trimCustomBuckets(histogram, cutoff, keepLower);
    double base = Math.pow(2d, Math.pow(2d, -histogram.schema()));
    BucketTrim positive =
        trimBuckets(
            histogram.positiveOffset(),
            histogram.positiveBuckets(),
            index -> new NativeBucket(Math.pow(base, index - 1d), Math.pow(base, index), 0d, true),
            cutoff,
            keepLower,
            keepLower,
            !keepLower);
    BucketTrim negative =
        trimBuckets(
            histogram.negativeOffset(),
            histogram.negativeBuckets(),
            index -> new NativeBucket(-Math.pow(base, index), -Math.pow(base, index - 1d), 0d, true),
            cutoff,
            keepLower,
            !keepLower,
            keepLower);
    boolean hasNegative = Arrays.stream(histogram.negativeBuckets()).anyMatch(count -> count != 0d);
    boolean hasPositive = Arrays.stream(histogram.positiveBuckets()).anyMatch(count -> count != 0d);
    NativeBucket zero =
        new NativeBucket(
            hasNegative || !hasPositive ? -histogram.zeroThreshold() : 0d,
            hasPositive || !hasNegative ? histogram.zeroThreshold() : 0d,
            histogram.zeroCount(),
            false);
    double zeroCount = selectedCount(zero, cutoff, keepLower);
    double zeroSum = selectedSum(zero, cutoff, keepLower);
    return new HistogramSeries.NativeHistogramSample(
        histogram.startMs(),
        histogram.endMs(),
        histogram.schema(),
        histogram.zeroThreshold(),
        zeroCount,
        positive.offset(),
        positive.buckets(),
        negative.offset(),
        negative.buckets(),
        histogram.customValues(),
        positive.sum() + negative.sum() + zeroSum,
        positive.count() + negative.count() + zeroCount,
        histogram.counterResetHint());
  }

  private static HistogramSeries.NativeHistogramSample trimCustomBuckets(
      HistogramSeries.NativeHistogramSample histogram, double cutoff, boolean keepLower) {
    double[] bounds = histogram.customValues();
    BucketTrim buckets =
        trimBuckets(
            histogram.positiveOffset(),
            histogram.positiveBuckets(),
            index ->
                new NativeBucket(
                    index == 0 && bounds.length > 0 && bounds[0] > 0d
                        ? 0d
                        : index == 0 ? Double.NEGATIVE_INFINITY : bounds[index - 1],
                    index < bounds.length ? bounds[index] : Double.POSITIVE_INFINITY,
                    0d,
                    false),
            cutoff,
            keepLower,
            keepLower,
            !keepLower);
    return new HistogramSeries.NativeHistogramSample(
        histogram.startMs(),
        histogram.endMs(),
        histogram.schema(),
        0d,
        0d,
        buckets.offset(),
        buckets.buckets(),
        0,
        new double[0],
        bounds,
        buckets.sum(),
        buckets.count(),
        histogram.counterResetHint());
  }

  private static BucketTrim trimBuckets(
      int offset,
      double[] counts,
      java.util.function.IntFunction<NativeBucket> bounds,
      double cutoff,
      boolean keepLower,
      boolean trimLeading,
      boolean trimTrailing) {
    double[] selected = new double[counts.length];
    double sum = 0d;
    double count = 0d;
    for (int i = 0; i < counts.length; i++) {
      NativeBucket bucket = bounds.apply(offset + i);
      bucket = new NativeBucket(bucket.lower(), bucket.upper(), counts[i], bucket.exponential());
      selected[i] = selectedCount(bucket, cutoff, keepLower);
      count += selected[i];
      sum += selectedSum(bucket, cutoff, keepLower);
    }
    int first = 0;
    int last = selected.length;
    if (trimLeading) while (first < last && selected[first] == 0d) first++;
    if (trimTrailing) while (last > first && selected[last - 1] == 0d) last--;
    return new BucketTrim(offset + first, Arrays.copyOfRange(selected, first, last), sum, count);
  }

  private static double selectedCount(NativeBucket bucket, double cutoff, boolean keepLower) {
    double lower = keepLower ? cutoff : Double.NEGATIVE_INFINITY;
    double upper = keepLower ? Double.POSITIVE_INFINITY : cutoff;
    return overlap(bucket, lower, upper);
  }

  private static double selectedSum(NativeBucket bucket, double cutoff, boolean keepLower) {
    double lower = Math.max(bucket.lower(), keepLower ? cutoff : Double.NEGATIVE_INFINITY);
    double upper = Math.min(bucket.upper(), keepLower ? Double.POSITIVE_INFINITY : cutoff);
    if (lower >= upper) return 0d;
    double count = selectedCount(bucket, cutoff, keepLower);
    if (!bucket.exponential()) {
      if (Double.isInfinite(lower)) return count * upper;
      if (Double.isInfinite(upper)) return count * lower;
      return count * (lower + upper) / 2d;
    }
    if (upper <= 0d) return -count * Math.sqrt((-lower) * (-upper));
    return count * Math.sqrt(lower * upper);
  }

  private static double overlap(NativeBucket bucket, double lower, double upper) {
    double overlapLower = Math.max(lower, bucket.lower());
    double overlapUpper = Math.min(upper, bucket.upper());
    if (overlapLower >= overlapUpper) return 0d;
    if (overlapLower <= bucket.lower() && overlapUpper >= bucket.upper()) return bucket.count();
    if (!bucket.exponential() && Double.isInfinite(bucket.lower())) {
      return overlapLower == Double.NEGATIVE_INFINITY ? bucket.count() : 0d;
    }
    if (!bucket.exponential() && Double.isInfinite(bucket.upper())) {
      return overlapUpper == Double.POSITIVE_INFINITY ? bucket.count() : 0d;
    }
    double positionLower = bucketPosition(bucket, overlapLower);
    double positionUpper = bucketPosition(bucket, overlapUpper);
    return bucket.count() * (positionUpper - positionLower);
  }

  private static double interpolate(NativeBucket bucket, double position) {
    if (!bucket.exponential()) {
      return bucket.lower() + (bucket.upper() - bucket.lower()) * position;
    }
    if (bucket.upper() <= 0d) {
      return -Math.exp(
          Math.log(-bucket.lower())
              + (Math.log(-bucket.upper()) - Math.log(-bucket.lower())) * position);
    }
    return Math.exp(
        Math.log(bucket.lower())
            + (Math.log(bucket.upper()) - Math.log(bucket.lower())) * position);
  }

  private static double bucketPosition(NativeBucket bucket, double value) {
    if (!bucket.exponential()) return (value - bucket.lower()) / (bucket.upper() - bucket.lower());
    if (bucket.upper() <= 0d) {
      return (Math.log(-value) - Math.log(-bucket.lower()))
          / (Math.log(-bucket.upper()) - Math.log(-bucket.lower()));
    }
    return (Math.log(value) - Math.log(bucket.lower()))
        / (Math.log(bucket.upper()) - Math.log(bucket.lower()));
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
    double included = 0d;
    double previousCount = 0d;
    double lowerBound = bounds.length > 0 && bounds[0] > 0d ? 0d : Double.NEGATIVE_INFINITY;
    for (int i = 0; i < cumulative.length; i++) {
      double upperBound = i < bounds.length ? bounds[i] : Double.POSITIVE_INFINITY;
      double bucketCount = cumulative[i] - previousCount;
      double overlapLower = Math.max(lower, lowerBound);
      double overlapUpper = Math.min(upper, upperBound);
      if (overlapLower < overlapUpper) {
        if (overlapLower <= lowerBound
            && overlapUpper >= upperBound
            || upperBound == Double.POSITIVE_INFINITY
                && upper == Double.POSITIVE_INFINITY) {
          included += bucketCount;
        } else if (!Double.isInfinite(lowerBound) && !Double.isInfinite(upperBound)) {
          included += bucketCount * (overlapUpper - overlapLower) / (upperBound - lowerBound);
        }
      }
      previousCount = cumulative[i];
      lowerBound = upperBound;
    }
    return included / total;
  }

  private static CumulativeBuckets normalize(List<ClassicBucket> input) {
    List<ClassicBucket> sorted = new ArrayList<>(input);
    sorted.sort(Comparator.comparingDouble(ClassicBucket::upperBound));
    List<Double> bounds = new ArrayList<>();
    List<Double> cumulative = new ArrayList<>();
    for (var bucket : sorted) {
      int last = bounds.size() - 1;
      if (last >= 0 && Double.compare(bounds.get(last), bucket.upperBound()) == 0) {
        cumulative.set(last, cumulative.get(last) + bucket.cumulativeCount());
      } else {
        bounds.add(bucket.upperBound());
        cumulative.add(bucket.cumulativeCount());
      }
    }
    for (int i = 1; i < cumulative.size(); i++) {
      cumulative.set(i, Math.max(cumulative.get(i - 1), cumulative.get(i)));
    }
    return new CumulativeBuckets(
        bounds.stream().mapToDouble(Double::doubleValue).toArray(),
        cumulative.stream().mapToDouble(Double::doubleValue).toArray());
  }

  private static Double parseBound(String value) {
    if (value.equalsIgnoreCase("+Inf") || value.equalsIgnoreCase("Inf"))
      return Double.POSITIVE_INFINITY;
    if (value.equalsIgnoreCase("-Inf")) return Double.NEGATIVE_INFINITY;
    try {
      return Double.parseDouble(value);
    } catch (NumberFormatException ignored) {
      return null;
    }
  }

  private record ClassicBucketKey(Map<String, String> labels, long timestamp) {}

  private record ClassicBucket(double upperBound, double cumulativeCount) {}

  private record NativeHistogram(
      SeriesSample sample, HistogramSeries.NativeHistogramSample histogram) {}

  private record ClassicHistogram(Set<String> metrics, List<ClassicBucket> buckets) {
    private ClassicHistogram() {
      this(new HashSet<>(), new ArrayList<>());
    }
  }

  private record CumulativeBuckets(double[] bounds, double[] cumulative) {}

  private record NativeBucket(double lower, double upper, double count, boolean exponential) {}

  private record BucketTrim(int offset, double[] buckets, double sum, double count) {}

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
      double q, RangeVectorResult rv, long rangeMs, EvalContext ctx) {
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

  static double quantileFromHistogram(double q, HistoScan hs) {
    List<Float> ubs = hs.getUbs();
    List<Integer> counts = hs.getCounts();
    if (counts == null || counts.isEmpty()) return Double.NaN;

    long total = 0;
    for (int c : counts) total += c;
    if (total <= 0) return Double.NaN;

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
    double lower, upper;
    if (k == 0) {
      lower = Double.NEGATIVE_INFINITY; upper = ubs.get(0);
    } else if (k < n) {
      lower = ubs.get(k - 1); upper = ubs.get(k);
    } else {
      lower = (n >= 1) ? ubs.get(n - 1) : Double.NEGATIVE_INFINITY;
      upper = Double.POSITIVE_INFINITY;
    }

    int inBucket = counts.get(k);
    if (inBucket <= 0) return Double.isInfinite(upper) ? lower : upper;

    double pos = (target - cum) / Math.max(inBucket, 1);
    if (Double.isInfinite(lower)) return upper;
    if (Double.isInfinite(upper)) return lower;
    return (double) (lower + pos * (upper - lower));
  }

  private static void collectPoints(
      HistogramSeries hs, long winStart, long winEnd, List<HistoScan> out) {
    for (var p : hs.getPoints()) {
      if (!overlaps(p.startMs(), p.endMs(), winStart, winEnd)) continue;
      if (!(p instanceof HistogramSeries.ExplicitHistogramSample explicit)) continue;
      float[] bounds = explicit.upperBounds();
      int[] counts = explicit.counts();
      List<Float> ubs = new ArrayList<>(bounds == null ? 0 : bounds.length);
      if (bounds != null) for (double b : bounds) ubs.add((float) b);
      List<Integer> cs = new ArrayList<>(counts == null ? 0 : counts.length);
      if (counts != null) for (int c : counts) cs.add(c);
      out.add(new HistoScan("", explicit.startMs(), explicit.endMs(), ubs, cs));
    }
  }

  private static boolean overlaps(long startMs, long endMs, long winStart, long winEnd) {
    return startMs <= winEnd && endMs >= winStart;
  }
}

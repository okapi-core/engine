/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.eval;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.okapi.metrics.pojos.results.GaugeScan;
import org.okapi.metrics.pojos.results.Scan;

/** Time-indexed Prometheus samples for a series that may contain histograms. */
public final class HistogramSeries extends Scan {

  public enum Temporality {
    DELTA,
    CUMULATIVE
  }

  private final String universalPath;
  private final List<SeriesSample> points;

  public HistogramSeries(String universalPath, List<? extends SeriesSample> points) {
    this.universalPath = universalPath;
    this.points = List.copyOf(Objects.requireNonNull(points, "points"));
  }

  public String getUniversalPath() {
    return universalPath;
  }

  public List<SeriesSample> getPoints() {
    return points;
  }

  public GaugeScan floatScan() {
    var timestamps = new java.util.ArrayList<Long>();
    var values = new java.util.ArrayList<Float>();
    for (var point : points) {
      if (point instanceof FloatSample sample) {
        timestamps.add(sample.endMs());
        values.add(sample.value());
      }
    }
    return GaugeScan.builder()
        .universalPath(universalPath)
        .timestamps(List.copyOf(timestamps))
        .values(List.copyOf(values))
        .build();
  }

  public sealed interface SeriesSample permits FloatSample, HistogramSample {
    long startMs();

    long endMs();
  }

  public record FloatSample(long startMs, long endMs, float value) implements SeriesSample {}

  public sealed interface HistogramSample extends SeriesSample
      permits ExplicitHistogramSample, NativeHistogramSample {

    double sum();

    double count();
  }

  public record ExplicitHistogramSample(
      long startMs,
      long endMs,
      Temporality temporality,
      float[] upperBounds,
      int[] counts,
      double sum,
      double count)
      implements HistogramSample {}

  public record NativeHistogramSample(
      long startMs,
      long endMs,
      int schema,
      double zeroThreshold,
      double zeroCount,
      int positiveOffset,
      double[] positiveBuckets,
      int negativeOffset,
      double[] negativeBuckets,
      double[] customValues,
      double sum,
      double count,
      String counterResetHint)
      implements HistogramSample {}

  public static boolean sameValue(HistogramSample left, HistogramSample right) {
    if (left instanceof ExplicitHistogramSample a && right instanceof ExplicitHistogramSample b) {
      return Arrays.equals(a.upperBounds(), b.upperBounds())
          && Arrays.equals(a.counts(), b.counts())
          && Double.compare(a.sum(), b.sum()) == 0
          && Double.compare(a.count(), b.count()) == 0;
    }
    if (left instanceof NativeHistogramSample a && right instanceof NativeHistogramSample b) {
      return a.schema() == b.schema()
          && Double.compare(a.zeroThreshold(), b.zeroThreshold()) == 0
          && Double.compare(a.zeroCount(), b.zeroCount()) == 0
          && a.positiveOffset() == b.positiveOffset()
          && Arrays.equals(a.positiveBuckets(), b.positiveBuckets())
          && a.negativeOffset() == b.negativeOffset()
          && Arrays.equals(a.negativeBuckets(), b.negativeBuckets())
          && Arrays.equals(a.customValues(), b.customValues())
          && Double.compare(a.sum(), b.sum()) == 0
          && Double.compare(a.count(), b.count()) == 0;
    }
    return false;
  }

  public static HistogramSample add(HistogramSample left, HistogramSample right) {
    if (left instanceof ExplicitHistogramSample a && right instanceof ExplicitHistogramSample b) {
      return new ExplicitHistogramSample(
          b.startMs(),
          b.endMs(),
          b.temporality(),
          b.upperBounds(),
          add(a.counts(), b.counts()),
          a.sum() + b.sum(),
          a.count() + b.count());
    }
    if (left instanceof NativeHistogramSample a && right instanceof NativeHistogramSample b) {
      return nativeResult(b, add(a.positiveBuckets(), b.positiveBuckets()),
          add(a.negativeBuckets(), b.negativeBuckets()), a.zeroCount() + b.zeroCount(),
          a.sum() + b.sum(), a.count() + b.count());
    }
    throw new IllegalArgumentException("cannot add different histogram representations");
  }

  public static HistogramSample subtract(HistogramSample left, HistogramSample right) {
    if (left instanceof ExplicitHistogramSample a && right instanceof ExplicitHistogramSample b) {
      return new ExplicitHistogramSample(
          a.startMs(),
          a.endMs(),
          a.temporality(),
          a.upperBounds(),
          subtract(a.counts(), b.counts()),
          a.sum() - b.sum(),
          a.count() - b.count());
    }
    if (left instanceof NativeHistogramSample a && right instanceof NativeHistogramSample b) {
      return nativeResult(a, subtract(a.positiveBuckets(), b.positiveBuckets()),
          subtract(a.negativeBuckets(), b.negativeBuckets()), a.zeroCount() - b.zeroCount(),
          a.sum() - b.sum(), a.count() - b.count());
    }
    throw new IllegalArgumentException("cannot subtract different histogram representations");
  }

  public static HistogramSample scale(HistogramSample histogram, double factor) {
    if (histogram instanceof ExplicitHistogramSample sample) {
      int[] counts = new int[sample.counts().length];
      for (int i = 0; i < counts.length; i++) counts[i] = (int) Math.round(sample.counts()[i] * factor);
      return new ExplicitHistogramSample(
          sample.startMs(),
          sample.endMs(),
          sample.temporality(),
          sample.upperBounds(),
          counts,
          sample.sum() * factor,
          sample.count() * factor);
    }
    if (histogram instanceof NativeHistogramSample sample) {
      return nativeResult(sample, scale(sample.positiveBuckets(), factor),
          scale(sample.negativeBuckets(), factor), sample.zeroCount() * factor,
          sample.sum() * factor, sample.count() * factor);
    }
    throw new IllegalArgumentException("unknown histogram representation");
  }

  public static boolean isReset(HistogramSample previous, HistogramSample current) {
    if (previous instanceof ExplicitHistogramSample a && current instanceof ExplicitHistogramSample b) {
      return b.count() < a.count() || decreased(a.counts(), b.counts());
    }
    if (previous instanceof NativeHistogramSample a && current instanceof NativeHistogramSample b) {
      return a.schema() != b.schema()
          || b.count() < a.count()
          || b.zeroCount() < a.zeroCount()
          || decreased(a.positiveBuckets(), b.positiveBuckets())
          || decreased(a.negativeBuckets(), b.negativeBuckets());
    }
    return true;
  }

  private static NativeHistogramSample nativeResult(
      NativeHistogramSample template,
      double[] positiveBuckets,
      double[] negativeBuckets,
      double zeroCount,
      double sum,
      double count) {
    return new NativeHistogramSample(
        template.startMs(),
        template.endMs(),
        template.schema(),
        template.zeroThreshold(),
        zeroCount,
        template.positiveOffset(),
        positiveBuckets,
        template.negativeOffset(),
        negativeBuckets,
        template.customValues(),
        sum,
        count,
        "gauge");
  }

  private static int[] add(int[] left, int[] right) {
    int[] result = Arrays.copyOf(right, Math.max(left.length, right.length));
    for (int i = 0; i < left.length; i++) result[i] += left[i];
    return result;
  }

  private static int[] subtract(int[] left, int[] right) {
    int[] result = Arrays.copyOf(left, Math.max(left.length, right.length));
    for (int i = 0; i < right.length; i++) result[i] -= right[i];
    return result;
  }

  private static double[] add(double[] left, double[] right) {
    double[] result = Arrays.copyOf(right, Math.max(left.length, right.length));
    for (int i = 0; i < left.length; i++) result[i] += left[i];
    return result;
  }

  private static double[] subtract(double[] left, double[] right) {
    double[] result = Arrays.copyOf(left, Math.max(left.length, right.length));
    for (int i = 0; i < right.length; i++) result[i] -= right[i];
    return result;
  }

  private static double[] scale(double[] values, double factor) {
    double[] result = Arrays.copyOf(values, values.length);
    for (int i = 0; i < result.length; i++) result[i] *= factor;
    return result;
  }

  private static boolean decreased(int[] previous, int[] current) {
    if (previous.length != current.length) return true;
    for (int i = 0; i < previous.length; i++) if (current[i] < previous[i]) return true;
    return false;
  }

  private static boolean decreased(double[] previous, double[] current) {
    if (previous.length != current.length) return true;
    for (int i = 0; i < previous.length; i++) if (current[i] < previous[i]) return true;
    return false;
  }
}

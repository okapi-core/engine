/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.eval;

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
}

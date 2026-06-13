/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.eval;

import java.util.List;
import java.util.Objects;
import org.okapi.metrics.pojos.results.Scan;

/** Time-indexed histogram samples for a single series. */
public final class HistogramSeries extends Scan {

  public enum Temporality {
    DELTA,
    CUMULATIVE
  }

  private final String universalPath;
  private final List<HistogramSample> points;

  public HistogramSeries(String universalPath, List<? extends HistogramSample> points) {
    this.universalPath = universalPath;
    this.points = List.copyOf(Objects.requireNonNull(points, "points"));
  }

  public String getUniversalPath() {
    return universalPath;
  }

  public List<HistogramSample> getPoints() {
    return points;
  }

  public sealed interface HistogramSample permits ExplicitHistogramSample, NativeHistogramSample {
    long startMs();

    long endMs();

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

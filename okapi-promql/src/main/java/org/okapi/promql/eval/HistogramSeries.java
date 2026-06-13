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

  public record HistogramPoint(
      long startMs, long endMs, float[] upperBounds, int[] counts, float sum, float count) {}

  private final String universalPath;
  private final List<HistogramPoint> points;

  public HistogramSeries(String universalPath, List<HistogramPoint> points) {
    this.universalPath = universalPath;
    this.points = Objects.requireNonNull(points, "points");
  }

  public String getUniversalPath() {
    return universalPath;
  }

  public List<HistogramPoint> getPoints() {
    return points;
  }
}

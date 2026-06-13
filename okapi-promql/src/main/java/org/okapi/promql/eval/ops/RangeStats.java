/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.eval.ops;

import org.okapi.metrics.pojos.results.GaugeScan;
import org.okapi.promql.eval.EvalContext;
import org.okapi.promql.eval.InstantVectorResult;
import org.okapi.promql.eval.HistogramSeries;
import org.okapi.promql.eval.RangeEvalContext;
import org.okapi.promql.eval.RangeVectorResult;
import org.okapi.promql.eval.Staleness;
import org.okapi.promql.eval.VectorData.*;

import java.util.ArrayList;
import java.util.List;

/** Pure functions over RangeVectorResult for window statistics. */
public final class RangeStats {
  private RangeStats() {}

  public static InstantVectorResult avg(RangeVectorResult rv, long rangeMs, EvalContext ctx, long anchorMs) {
    return mapWindows(rv, rangeMs, ctx, anchorMs, (ts, vals, winStart, t) -> {
      double sum = 0; int count = 0;
      for (int i = 0; i < ts.size(); i++) {
        if (ts.get(i) <= winStart || ts.get(i) > t) continue;
        sum += vals.get(i); count++;
      }
      return count > 0 ? (float) (sum / count) : Float.NaN;
    });
  }

  public static InstantVectorResult min(RangeVectorResult rv, long rangeMs, EvalContext ctx, long anchorMs) {
    return mapWindows(rv, rangeMs, ctx, anchorMs, (ts, vals, winStart, t) -> {
      float min = Float.POSITIVE_INFINITY; int count = 0;
      for (int i = 0; i < ts.size(); i++) {
        if (ts.get(i) <= winStart || ts.get(i) > t) continue;
        if (vals.get(i) < min) min = vals.get(i); count++;
      }
      return count > 0 ? min : Float.NaN;
    });
  }

  public static InstantVectorResult max(RangeVectorResult rv, long rangeMs, EvalContext ctx, long anchorMs) {
    return mapWindows(rv, rangeMs, ctx, anchorMs, (ts, vals, winStart, t) -> {
      float max = Float.NEGATIVE_INFINITY; int count = 0;
      for (int i = 0; i < ts.size(); i++) {
        if (ts.get(i) <= winStart || ts.get(i) > t) continue;
        if (vals.get(i) > max) max = vals.get(i); count++;
      }
      return count > 0 ? max : Float.NaN;
    });
  }

  public static InstantVectorResult sum(RangeVectorResult rv, long rangeMs, EvalContext ctx, long anchorMs) {
    return mapWindows(rv, rangeMs, ctx, anchorMs, (ts, vals, winStart, t) -> {
      float s = 0;
      for (int i = 0; i < ts.size(); i++) {
        if (ts.get(i) <= winStart || ts.get(i) > t) continue;
        s += vals.get(i);
      }
      return s;
    });
  }

  public static InstantVectorResult count(RangeVectorResult rv, long rangeMs, EvalContext ctx, long anchorMs) {
    return mapWindows(rv, rangeMs, ctx, anchorMs, (ts, vals, winStart, t) -> {
      int c = 0;
      for (int i = 0; i < ts.size(); i++) {
        if (ts.get(i) <= winStart || ts.get(i) > t) continue;
        c++;
      }
      return (float) c;
    });
  }

  public static InstantVectorResult last(RangeVectorResult rv, long rangeMs, EvalContext ctx, long anchorMs) {
    return mapWindowsPreserveName(rv, rangeMs, ctx, anchorMs, (ts, vals, winStart, t) -> {
      for (int i = ts.size() - 1; i >= 0; --i) {
        if (ts.get(i) <= winStart || ts.get(i) > t) continue;
        return vals.get(i);
      }
      return Float.NaN;
    });
  }

  public static InstantVectorResult present(RangeVectorResult rv, long rangeMs, EvalContext ctx, long anchorMs) {
    return mapWindows(rv, rangeMs, ctx, anchorMs, (ts, vals, winStart, t) -> {
      for (int i = 0; i < ts.size(); i++) {
        if (ts.get(i) > winStart && ts.get(i) <= t) return 1f;
      }
      return 0f;
    });
  }

  public static InstantVectorResult first(RangeVectorResult rv, long rangeMs, EvalContext ctx, long anchorMs) {
    return mapWindowsPreserveName(rv, rangeMs, ctx, anchorMs, (ts, vals, winStart, t) -> {
      for (int i = 0; i < ts.size(); i++) {
        if (ts.get(i) <= winStart || ts.get(i) > t) continue;
        return vals.get(i);
      }
      return Float.NaN;
    });
  }

  public static InstantVectorResult stddev(RangeVectorResult rv, long rangeMs, EvalContext ctx, long anchorMs) {
    return mapWindows(rv, rangeMs, ctx, anchorMs, (ts, vals, winStart, t) -> {
      double sum = 0; int count = 0;
      for (int i = 0; i < ts.size(); i++) {
        if (ts.get(i) <= winStart || ts.get(i) > t) continue;
        sum += vals.get(i); count++;
      }
      if (count == 0) return Float.NaN;
      double mean = sum / count;
      double var = 0;
      for (int i = 0; i < ts.size(); i++) {
        if (ts.get(i) <= winStart || ts.get(i) > t) continue;
        double d = vals.get(i) - mean; var += d * d;
      }
      return (float) Math.sqrt(var / count);
    });
  }

  public static InstantVectorResult stdvar(RangeVectorResult rv, long rangeMs, EvalContext ctx, long anchorMs) {
    return mapWindows(rv, rangeMs, ctx, anchorMs, (ts, vals, winStart, t) -> {
      double sum = 0; int count = 0;
      for (int i = 0; i < ts.size(); i++) {
        if (ts.get(i) <= winStart || ts.get(i) > t) continue;
        sum += vals.get(i); count++;
      }
      if (count == 0) return Float.NaN;
      double mean = sum / count;
      double var = 0;
      for (int i = 0; i < ts.size(); i++) {
        if (ts.get(i) <= winStart || ts.get(i) > t) continue;
        double d = vals.get(i) - mean; var += d * d;
      }
      return (float) (var / count);
    });
  }

  public static InstantVectorResult mad(RangeVectorResult rv, long rangeMs, EvalContext ctx, long anchorMs) {
    return mapWindows(rv, rangeMs, ctx, anchorMs, (ts, vals, winStart, t) -> {
      List<Float> window = new ArrayList<>();
      for (int i = 0; i < ts.size(); i++) {
        if (ts.get(i) <= winStart || ts.get(i) > t) continue;
        window.add(vals.get(i));
      }
      if (window.isEmpty()) return Float.NaN;
      window.sort(Float::compare);
      float median = window.size() % 2 == 1
          ? window.get(window.size() / 2)
          : (window.get(window.size() / 2 - 1) + window.get(window.size() / 2)) / 2f;
      List<Float> diffs = new ArrayList<>(window.size());
      for (float v : window) diffs.add(Math.abs(v - median));
      diffs.sort(Float::compare);
      return diffs.size() % 2 == 1
          ? diffs.get(diffs.size() / 2)
          : (diffs.get(diffs.size() / 2 - 1) + diffs.get(diffs.size() / 2)) / 2f;
    });
  }

  public static InstantVectorResult changes(RangeVectorResult rv, RangeEvalContext rangeCtx, long anchorMs) {
    return mapSeriesWindows(rv, rangeCtx, anchorMs, points -> {
      float count = 0;
      SeriesPoint prev = null;
      for (SeriesPoint point : points) {
        if (prev != null && !sameValue(prev.value(), point.value())) count++;
        prev = point;
      }
      return count;
    });
  }

  public static InstantVectorResult resets(RangeVectorResult rv, RangeEvalContext rangeCtx, long anchorMs) {
    return mapSeriesWindows(rv, rangeCtx, anchorMs, points -> {
      float count = 0;
      SeriesPoint prev = null;
      for (SeriesPoint point : points) {
        if (prev != null && isReset(prev.value(), point.value())) count++;
        prev = point;
      }
      return count;
    });
  }

  @FunctionalInterface
  private interface SeriesWindowFn {
    float apply(List<SeriesPoint> points);
  }

  private static InstantVectorResult mapSeriesWindows(
      RangeVectorResult rv, RangeEvalContext rangeCtx, long anchorMs, SeriesWindowFn fn) {
    List<SeriesSample> out = new ArrayList<>();
    EvalContext ctx = rangeCtx.query();
    for (SeriesWindow window : rv.data()) {
      for (long t = ctx.startMs; t <= ctx.endMs; t += ctx.stepMs) {
        long anchor = anchorMs >= 0 ? anchorMs : t;
        List<SeriesPoint> points = pointsInWindow(window, rangeCtx, anchor);
        out.add(new SeriesSample(SeriesIds.derived(window.id()), new Sample(t, fn.apply(points))));
      }
    }
    return new InstantVectorResult(out);
  }

  private static List<SeriesPoint> pointsInWindow(
      SeriesWindow window, RangeEvalContext rangeCtx, long anchor) {
    if (window.scan() instanceof GaugeScan scan) {
      List<SeriesPoint> points = new ArrayList<>();
      for (Point point : pointsInWindow(scan.getTimestamps(), scan.getValues(), rangeCtx, anchor))
        if (!Staleness.isStale(point.value())) points.add(new SeriesPoint(point.ts(), point.value()));
      return points;
    }
    if (window.scan() instanceof HistogramSeries series) {
      List<SeriesPoint> points = new ArrayList<>();
      for (var point : series.getPoints()) {
        if (!rangeCtx.includes(point.endMs(), anchor)) continue;
        if (point instanceof HistogramSeries.FloatSample sample) {
          if (!Staleness.isStale(sample.value())) points.add(new SeriesPoint(point.endMs(), sample.value()));
        } else if (point instanceof HistogramSeries.HistogramSample histogram) {
          points.add(new SeriesPoint(point.endMs(), histogram));
        }
      }
      return points;
    }
    return List.of();
  }

  private static boolean sameValue(Object left, Object right) {
    if (left instanceof Float a && right instanceof Float b) return Float.compare(a, b) == 0;
    if (left instanceof HistogramSeries.NativeHistogramSample a
        && right instanceof HistogramSeries.NativeHistogramSample b) {
      return a.schema() == b.schema()
          && Double.compare(a.zeroThreshold(), b.zeroThreshold()) == 0
          && Double.compare(a.zeroCount(), b.zeroCount()) == 0
          && a.positiveOffset() == b.positiveOffset()
          && java.util.Arrays.equals(a.positiveBuckets(), b.positiveBuckets())
          && a.negativeOffset() == b.negativeOffset()
          && java.util.Arrays.equals(a.negativeBuckets(), b.negativeBuckets())
          && java.util.Arrays.equals(a.customValues(), b.customValues())
          && Double.compare(a.sum(), b.sum()) == 0
          && Double.compare(a.count(), b.count()) == 0;
    }
    return left.equals(right);
  }

  private static boolean isReset(Object previous, Object current) {
    if (previous instanceof Float a && current instanceof Float b) return b < a;
    if (previous instanceof HistogramSeries.NativeHistogramSample a
        && current instanceof HistogramSeries.NativeHistogramSample b) {
      return a.schema() != b.schema()
          || b.count() < a.count()
          || b.zeroCount() < a.zeroCount()
          || bucketsDecreased(a.positiveBuckets(), b.positiveBuckets())
          || bucketsDecreased(a.negativeBuckets(), b.negativeBuckets());
    }
    return !previous.getClass().equals(current.getClass());
  }

  private static boolean bucketsDecreased(double[] previous, double[] current) {
    if (previous.length != current.length) return true;
    for (int i = 0; i < previous.length; i++) if (current[i] < previous[i]) return true;
    return false;
  }

  private record SeriesPoint(long ts, Object value) {}

  private static List<Point> pointsInWindow(
      List<Long> ts, List<Float> vals, RangeEvalContext rangeCtx, long anchor) {
    if (rangeCtx.mode() != org.okapi.promql.eval.nodes.ExtendedVectorMode.ANCHORED) {
      List<Point> points = new ArrayList<>();
      for (int i = 0; i < ts.size(); i++) {
        if (rangeCtx.includes(ts.get(i), anchor)) points.add(new Point(ts.get(i), vals.get(i)));
      }
      return points;
    }

    long start = rangeCtx.windowStart(anchor);
    int first = 0;
    while (first < ts.size() && ts.get(first) <= start) first++;
    first = Math.max(0, first - 1);
    List<Point> points = new ArrayList<>();
    for (int i = first; i < ts.size() && ts.get(i) <= anchor; i++) {
      long timestamp = ts.get(i);
      if (timestamp < start) points.add(new Point(start, vals.get(i)));
      else points.add(new Point(timestamp, vals.get(i)));
    }
    return points;
  }

  private record Point(long ts, float value) {}

  public static InstantVectorResult quantile(
      float q, RangeVectorResult rv, long rangeMs, EvalContext ctx, long anchorMs) {
    return mapWindows(rv, rangeMs, ctx, anchorMs, (ts, vals, winStart, t) -> {
      if (Float.isNaN(q)) return Float.NaN;
      if (q < 0) return Float.NEGATIVE_INFINITY;
      if (q > 1) return Float.POSITIVE_INFINITY;
      List<Float> window = new ArrayList<>();
      for (int i = 0; i < ts.size(); i++) {
        if (ts.get(i) <= winStart || ts.get(i) > t) continue;
        window.add(vals.get(i));
      }
      if (window.isEmpty()) return Float.NaN;
      window.sort(Float::compare);
      double rank = q * (window.size() - 1);
      int lo = (int) Math.floor(rank), hi = (int) Math.ceil(rank);
      if (lo == hi) return window.get(lo);
      return window.get(lo) + (float) (rank - lo) * (window.get(hi) - window.get(lo));
    });
  }

  @FunctionalInterface
  private interface WindowFn {
    float apply(List<Long> ts, List<Float> vals, long winStart, long t);
  }

  // anchorMs >= 0 pins the window anchor to a fixed time (for @ modifier); -1 uses the step time.
  private static InstantVectorResult mapWindows(
      RangeVectorResult rv, long rangeMs, EvalContext ctx, long anchorMs, WindowFn fn) {
    return mapWindows(rv, rangeMs, ctx, anchorMs, fn, true);
  }

  private static InstantVectorResult mapWindowsPreserveName(
      RangeVectorResult rv, long rangeMs, EvalContext ctx, long anchorMs, WindowFn fn) {
    return mapWindows(rv, rangeMs, ctx, anchorMs, fn, false);
  }

  private static InstantVectorResult mapWindows(
      RangeVectorResult rv, long rangeMs, EvalContext ctx, long anchorMs, WindowFn fn, boolean derived) {
    List<SeriesSample> out = new ArrayList<>();
    for (SeriesWindow w : rv.data()) {
      if (!(w.scan() instanceof GaugeScan gs)) continue;
      var normalized = Staleness.withoutStaleSamples(gs);
      var ts = normalized.getTimestamps();
      var vals = normalized.getValues();
      for (long t = ctx.startMs; t <= ctx.endMs; t += ctx.stepMs) {
        long anchor = anchorMs >= 0 ? anchorMs : t;
        float v = fn.apply(ts, vals, anchor - rangeMs, anchor);
        out.add(new SeriesSample(derived ? SeriesIds.derived(w.id()) : w.id(), new Sample(t, v)));
      }
    }
    return new InstantVectorResult(out);
  }

}

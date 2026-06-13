/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.eval.ops;

import org.okapi.metrics.pojos.results.GaugeScan;
import org.okapi.metrics.pojos.results.Scan;
import org.okapi.metrics.pojos.results.SumScan;
import org.okapi.promql.eval.EvalContext;
import org.okapi.promql.eval.HistogramSeries;
import org.okapi.promql.eval.InstantVectorResult;
import org.okapi.promql.eval.RangeEvalContext;
import org.okapi.promql.eval.RangeVectorResult;
import org.okapi.promql.eval.Staleness;
import org.okapi.promql.eval.VectorData.*;
import org.okapi.promql.eval.nodes.ExtendedVectorMode;

import java.util.ArrayList;
import java.util.List;

/** Pure functions over RangeVectorResult for counter/gauge transforms. */
public final class RangeFunctions {
  private RangeFunctions() {}

  // anchorMs >= 0 pins the window anchor (for @ modifier); -1 uses the step time.

  public static InstantVectorResult rate(RangeVectorResult rv, RangeEvalContext rangeCtx, long anchorMs) {
    long rangeMs = rangeCtx.rangeMs();
    EvalContext ctx = rangeCtx.query();
    List<SeriesSample> out = new ArrayList<>();
    for (SeriesWindow w : rv.data()) {
      if (!(w.scan() instanceof SumScan) && !(w.scan() instanceof GaugeScan)) {
        continue;
      }
      for (long t = ctx.startMs; t <= ctx.endMs; t += ctx.stepMs) {
        long anchor = anchorMs >= 0 ? anchorMs : t;
        float inc = w.scan() instanceof SumScan ss
            ? sumInWindow(ss, anchor - rangeMs, anchor)
            : counterIncrease((GaugeScan) w.scan(), rangeCtx, anchor);
        float v = (rangeMs > 0) ? inc / (rangeMs / 1000f) : Float.NaN;
        out.add(new SeriesSample(SeriesIds.derived(w.id()), new Sample(t, v)));
      }
    }
    return new InstantVectorResult(out);
  }

  public static InstantVectorResult irate(RangeVectorResult rv, long rangeMs, EvalContext ctx, long anchorMs) {
    List<SeriesSample> out = new ArrayList<>();
    for (SeriesWindow w : rv.data()) {
      if (!(w.scan() instanceof SumScan) && !(w.scan() instanceof GaugeScan)) continue;
      for (long t = ctx.startMs; t <= ctx.endMs; t += ctx.stepMs) {
        long anchor = anchorMs >= 0 ? anchorMs : t;
        float v = w.scan() instanceof SumScan ss
            ? irateInWindow(ss, anchor - rangeMs, anchor)
            : sampledCounterIrate((GaugeScan) w.scan(), anchor - rangeMs, anchor);
        out.add(new SeriesSample(SeriesIds.derived(w.id()), new Sample(t, v)));
      }
    }
    return new InstantVectorResult(out);
  }

  public static InstantVectorResult increase(RangeVectorResult rv, RangeEvalContext rangeCtx, long anchorMs) {
    long rangeMs = rangeCtx.rangeMs();
    EvalContext ctx = rangeCtx.query();
    List<SeriesSample> out = new ArrayList<>();
    for (SeriesWindow w : rv.data()) {
      if (!(w.scan() instanceof SumScan) && !(w.scan() instanceof GaugeScan)) continue;
      for (long t = ctx.startMs; t <= ctx.endMs; t += ctx.stepMs) {
        long anchor = anchorMs >= 0 ? anchorMs : t;
        float v = w.scan() instanceof SumScan ss
            ? sumInWindow(ss, anchor - rangeMs, anchor)
            : counterIncrease((GaugeScan) w.scan(), rangeCtx, anchor);
        out.add(new SeriesSample(SeriesIds.derived(w.id()), new Sample(t, v)));
      }
    }
    return new InstantVectorResult(out);
  }

  public static InstantVectorResult delta(RangeVectorResult rv, RangeEvalContext rangeCtx, long anchorMs) {
    long rangeMs = rangeCtx.rangeMs();
    EvalContext ctx = rangeCtx.query();
    List<SeriesSample> out = new ArrayList<>();
    for (SeriesWindow w : rv.data()) {
      GaugeScan gs = floatScan(w.scan());
      if (gs == null) continue;
      for (long t = ctx.startMs; t <= ctx.endMs; t += ctx.stepMs) {
        long anchor = anchorMs >= 0 ? anchorMs : t;
        out.add(new SeriesSample(SeriesIds.derived(w.id()), new Sample(t, delta(gs, rangeCtx, anchor))));
      }
    }
    return new InstantVectorResult(out);
  }

  public static InstantVectorResult idelta(RangeVectorResult rv, long rangeMs, EvalContext ctx, long anchorMs) {
    List<SeriesSample> out = new ArrayList<>();
    for (SeriesWindow w : rv.data()) {
      GaugeScan gs = floatScan(w.scan());
      if (gs == null) continue;
      for (long t = ctx.startMs; t <= ctx.endMs; t += ctx.stepMs) {
        long anchor = anchorMs >= 0 ? anchorMs : t;
        out.add(new SeriesSample(SeriesIds.derived(w.id()), new Sample(t, ideltaInWindow(gs, anchor - rangeMs, anchor))));
      }
    }
    return new InstantVectorResult(out);
  }

  public static InstantVectorResult deriv(RangeVectorResult rv, long rangeMs, EvalContext ctx, long anchorMs) {
    List<SeriesSample> out = new ArrayList<>();
    for (SeriesWindow w : rv.data()) {
      GaugeScan gs = floatScan(w.scan());
      if (gs == null) continue;
      for (long t = ctx.startMs; t <= ctx.endMs; t += ctx.stepMs) {
        long anchor = anchorMs >= 0 ? anchorMs : t;
        out.add(new SeriesSample(SeriesIds.derived(w.id()), new Sample(t, derivInWindow(gs, anchor - rangeMs, anchor))));
      }
    }
    return new InstantVectorResult(out);
  }

  public static InstantVectorResult predictLinear(
      RangeVectorResult rv, long rangeMs, EvalContext ctx, long anchorMs, float t) {
    List<SeriesSample> out = new ArrayList<>();
    for (SeriesWindow w : rv.data()) {
      GaugeScan gs = floatScan(w.scan());
      if (gs == null) continue;
      gs = Staleness.withoutStaleSamples(gs);
      var ts = gs.getTimestamps();
      var vals = gs.getValues();
      for (long step = ctx.startMs; step <= ctx.endMs; step += ctx.stepMs) {
        long anchor = anchorMs >= 0 ? anchorMs : step;
        double n = 0, sumX = 0, sumY = 0, sumXX = 0, sumXY = 0;
        for (int i = 0; i < ts.size(); i++) {
          long tsi = ts.get(i);
          if (tsi <= anchor - rangeMs || tsi > anchor) continue;
          double x = tsi / 1000.0, y = vals.get(i);
          n++; sumX += x; sumY += y; sumXX += x * x; sumXY += x * y;
        }
        float v;
        if (n < 2) {
          v = Float.NaN;
        } else {
          double denom = n * sumXX - sumX * sumX;
          double slope = denom == 0 ? 0 : (n * sumXY - sumX * sumY) / denom;
          double intercept = (sumY - slope * sumX) / n;
          v = (float) (slope * (step / 1000.0 + t) + intercept);
        }
        out.add(new SeriesSample(SeriesIds.derived(w.id()), new Sample(step, v)));
      }
    }
    return new InstantVectorResult(out);
  }

  public static InstantVectorResult doubleExponentialSmoothing(
      RangeVectorResult rv,
      long rangeMs,
      EvalContext ctx,
      long anchorMs,
      float smoothingFactor,
      float trendFactor) {
    List<SeriesSample> out = new ArrayList<>();
    for (SeriesWindow window : rv.data()) {
      GaugeScan scan = floatScan(window.scan());
      if (scan == null) continue;
      scan = Staleness.withoutStaleSamples(scan);
      for (long t = ctx.startMs; t <= ctx.endMs; t += ctx.stepMs) {
        long anchor = anchorMs >= 0 ? anchorMs : t;
        Float value =
            smoothInWindow(scan, anchor - rangeMs, anchor, smoothingFactor, trendFactor);
        if (value != null)
          out.add(new SeriesSample(SeriesIds.derived(window.id()), new Sample(t, value)));
      }
    }
    return new InstantVectorResult(out);
  }

  // --- window computations ---

  private static float sumInWindow(SumScan ss, long start, long end) {
    float total = 0f;
    var ts = ss.getTs();
    var cnt = ss.getCounts();
    for (int i = 0; i < ts.size(); i++) {
      long tsi = ts.get(i);
      if (tsi <= start || tsi > end) continue;
      total += cnt.get(i);
    }
    return total;
  }

  private static float irateInWindow(SumScan ss, long start, long end) {
    var ts = ss.getTs();
    var cnt = ss.getCounts();
    Integer lastIdx = null, prevIdx = null;
    for (int i = ts.size() - 1; i >= 0; --i) {
      long tsi = ts.get(i);
      if (tsi <= start || tsi > end) continue;
      if (lastIdx == null) lastIdx = i;
      else {
        prevIdx = i;
        break;
      }
    }
    if (lastIdx == null || prevIdx == null) return Float.NaN;
    float delta = cnt.get(lastIdx);
    float seconds = Math.max((ts.get(lastIdx) - ts.get(prevIdx)) / 1000f, 1f);
    return delta / seconds;
  }

  private static float sampledCounterIncrease(GaugeScan gs, long start, long end) {
    gs = Staleness.withoutStaleSamples(gs);
    var ts = gs.getTimestamps();
    var vals = gs.getValues();
    int firstIdx = -1, lastIdx = -1;
    for (int i = 0; i < ts.size(); i++) {
      long tsi = ts.get(i);
      if (tsi <= start || tsi > end) continue;
      if (firstIdx == -1) firstIdx = i;
      lastIdx = i;
    }
    if (firstIdx == -1 || firstIdx == lastIdx) return Float.NaN;

    float result = vals.get(lastIdx) - vals.get(firstIdx);
    for (int i = firstIdx + 1; i <= lastIdx; i++) {
      if (vals.get(i) < vals.get(i - 1)) result += vals.get(i - 1);
    }

    double sampledInterval = ts.get(lastIdx) - ts.get(firstIdx);
    double averageInterval = sampledInterval / (lastIdx - firstIdx);
    double durationToStart = ts.get(firstIdx) - start;
    double durationToEnd = end - ts.get(lastIdx);
    double extrapolationThreshold = averageInterval * 1.1;
    if (durationToStart >= extrapolationThreshold) durationToStart = averageInterval / 2;
    if (result > 0 && vals.get(firstIdx) >= 0) {
      double durationToZero = sampledInterval * (vals.get(firstIdx) / result);
      if (durationToZero < durationToStart) durationToStart = durationToZero;
    }
    if (durationToEnd >= extrapolationThreshold) durationToEnd = averageInterval / 2;
    return (float) (result * (sampledInterval + durationToStart + durationToEnd) / sampledInterval);
  }

  private static float counterIncrease(GaugeScan gs, RangeEvalContext rangeCtx, long anchor) {
    return switch (rangeCtx.mode()) {
      case NONE -> sampledCounterIncrease(gs, rangeCtx.windowStart(anchor), anchor);
      case ANCHORED, SMOOTHED -> extendedRate(gs, rangeCtx, anchor, true);
    };
  }

  private static float delta(GaugeScan gs, RangeEvalContext rangeCtx, long anchor) {
    return switch (rangeCtx.mode()) {
      case NONE -> deltaInWindow(gs, rangeCtx.windowStart(anchor), anchor);
      case ANCHORED, SMOOTHED -> extendedRate(gs, rangeCtx, anchor, false);
    };
  }

  private static float extendedRate(
      GaugeScan gs, RangeEvalContext rangeCtx, long anchor, boolean counter) {
    gs = Staleness.withoutStaleSamples(gs);
    var ts = gs.getTimestamps();
    var vals = gs.getValues();
    if (ts.isEmpty()) return Float.NaN;
    if (ts.size() == 1) return 0f;

    long start = rangeCtx.windowStart(anchor);
    int first = Math.max(0, firstAfter(ts, start) - 1);
    int last = ts.size() - 1;
    if (rangeCtx.mode() == ExtendedVectorMode.SMOOTHED) {
      last = Math.min(last, firstAtOrAfter(ts, anchor));
    }
    if (ts.get(last) <= start
        || (rangeCtx.mode() == ExtendedVectorMode.SMOOTHED && ts.get(first) > anchor)) {
      return 0f;
    }

    Point left = rangeCtx.mode() == ExtendedVectorMode.SMOOTHED
        ? pickOrInterpolateLeft(ts, vals, first, start, counter)
        : pickAnchoredLeft(ts, vals, first, start);
    Point right = rangeCtx.mode() == ExtendedVectorMode.SMOOTHED
        ? pickOrInterpolateRight(ts, vals, last, anchor, counter)
        : pickAnchoredRight(ts, vals, last, anchor);

    float result = right.value() - left.value();
    if (!counter) return result;

    float previous = left.value();
    if (ts.get(first) <= start) first++;
    if (ts.get(last) >= anchor) last--;
    for (int i = first; i <= last; i++) {
      float current = vals.get(i);
      if (current < previous) result += previous;
      previous = current;
    }
    if (right.value() < previous) result += previous;
    return result;
  }

  private static Point pickOrInterpolateLeft(
      List<Long> ts, List<Float> vals, int first, long start, boolean counter) {
    if (first == ts.size() - 1 || ts.get(first) >= start) return point(ts, vals, first);
    return new Point(start, interpolate(point(ts, vals, first + 1), point(ts, vals, first), start, counter));
  }

  private static Point pickOrInterpolateRight(
      List<Long> ts, List<Float> vals, int last, long end, boolean counter) {
    if (last == 0 || ts.get(last) <= end) return point(ts, vals, last);
    return new Point(end, interpolate(point(ts, vals, last), point(ts, vals, last - 1), end, counter));
  }

  private static Point pickAnchoredLeft(List<Long> ts, List<Float> vals, int first, long start) {
    Point point = point(ts, vals, first);
    return point.ts() >= start ? point : new Point(start, point.value());
  }

  private static Point pickAnchoredRight(List<Long> ts, List<Float> vals, int last, long end) {
    Point point = point(ts, vals, last);
    return point.ts() <= end ? point : new Point(end, point.value());
  }

  private static float interpolate(Point later, Point earlier, long target, boolean counter) {
    float earlierValue = earlier.value();
    if (counter && later.value() < earlierValue) earlierValue = 0f;
    return (float) (earlierValue
        + (later.value() - earlierValue) * (target - earlier.ts()) / (double) (later.ts() - earlier.ts()));
  }

  private static int firstAfter(List<Long> timestamps, long target) {
    int i = 0;
    while (i < timestamps.size() && timestamps.get(i) <= target) i++;
    return i;
  }

  private static int firstAtOrAfter(List<Long> timestamps, long target) {
    int i = 0;
    while (i < timestamps.size() && timestamps.get(i) < target) i++;
    return i;
  }

  private static Point point(List<Long> ts, List<Float> vals, int index) {
    return new Point(ts.get(index), vals.get(index));
  }

  private record Point(long ts, float value) {}

  private static float sampledCounterIrate(GaugeScan gs, long start, long end) {
    gs = Staleness.withoutStaleSamples(gs);
    var ts = gs.getTimestamps();
    var vals = gs.getValues();
    Integer lastIdx = null, prevIdx = null;
    for (int i = ts.size() - 1; i >= 0; --i) {
      long tsi = ts.get(i);
      if (tsi <= start || tsi > end) continue;
      if (lastIdx == null) lastIdx = i;
      else {
        prevIdx = i;
        break;
      }
    }
    if (lastIdx == null || prevIdx == null) return Float.NaN;
    float delta = vals.get(lastIdx) - vals.get(prevIdx);
    if (delta < 0) delta = vals.get(lastIdx);
    float seconds = Math.max((ts.get(lastIdx) - ts.get(prevIdx)) / 1000f, 1f);
    return delta / seconds;
  }

  private static float deltaInWindow(GaugeScan gs, long start, long end) {
    gs = Staleness.withoutStaleSamples(gs);
    var ts = gs.getTimestamps();
    var vals = gs.getValues();
    int firstIdx = -1, lastIdx = -1;
    for (int i = 0; i < ts.size(); i++) {
      long tsi = ts.get(i);
      if (tsi < start || tsi > end) continue;
      if (firstIdx == -1) firstIdx = i;
      lastIdx = i;
    }
    if (firstIdx == -1) return Float.NaN;
    return vals.get(lastIdx) - vals.get(firstIdx);
  }

  private static float ideltaInWindow(GaugeScan gs, long start, long end) {
    gs = Staleness.withoutStaleSamples(gs);
    var ts = gs.getTimestamps();
    var vals = gs.getValues();
    Integer lastIdx = null, prevIdx = null;
    for (int i = ts.size() - 1; i >= 0; --i) {
      long tsi = ts.get(i);
      if (tsi <= start || tsi > end) continue;
      if (lastIdx == null) lastIdx = i;
      else {
        prevIdx = i;
        break;
      }
    }
    if (lastIdx == null || prevIdx == null) return Float.NaN;
    return vals.get(lastIdx) - vals.get(prevIdx);
  }

  private static float derivInWindow(GaugeScan gs, long start, long end) {
    gs = Staleness.withoutStaleSamples(gs);
    var ts = gs.getTimestamps();
    var vals = gs.getValues();
    double n = 0, sumX = 0, sumY = 0, sumXX = 0, sumXY = 0;
    for (int i = 0; i < ts.size(); i++) {
      long tsi = ts.get(i);
      if (tsi <= start || tsi > end) continue;
      double x = tsi / 1000d;
      double y = vals.get(i);
      n++;
      sumX += x;
      sumY += y;
      sumXX += x * x;
      sumXY += x * y;
    }
    if (n < 2) return Float.NaN;
    double covariance = n * sumXY - sumX * sumY;
    double variance = n * sumXX - sumX * sumX;
    return (float) (covariance / variance);
  }

  private static Float smoothInWindow(
      GaugeScan scan, long start, long end, float smoothingFactor, float trendFactor) {
    var timestamps = scan.getTimestamps();
    var values = scan.getValues();
    Float level = null;
    float trend = 0;
    boolean initializedTrend = false;
    for (int i = 0; i < timestamps.size(); i++) {
      long timestamp = timestamps.get(i);
      if (timestamp <= start || timestamp > end) continue;
      float value = values.get(i);
      if (level == null) {
        level = value;
        continue;
      }
      if (!initializedTrend) {
        trend = value - level;
        initializedTrend = true;
      }
      float previousLevel = level;
      level = smoothingFactor * value + (1 - smoothingFactor) * (level + trend);
      trend = trendFactor * (level - previousLevel) + (1 - trendFactor) * trend;
    }
    return level;
  }

  private static GaugeScan floatScan(Scan scan) {
    if (scan instanceof GaugeScan gauge) return gauge;
    if (scan instanceof HistogramSeries series) return series.floatScan();
    return null;
  }
}

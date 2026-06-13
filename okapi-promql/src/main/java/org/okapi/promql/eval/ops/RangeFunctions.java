/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.eval.ops;

import org.okapi.metrics.pojos.results.GaugeScan;
import org.okapi.metrics.pojos.results.SumScan;
import org.okapi.promql.eval.EvalContext;
import org.okapi.promql.eval.InstantVectorResult;
import org.okapi.promql.eval.RangeVectorResult;
import org.okapi.promql.eval.VectorData.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Pure functions over RangeVectorResult for counter/gauge transforms. */
public final class RangeFunctions {
  private RangeFunctions() {}

  // anchorMs >= 0 pins the window anchor (for @ modifier); -1 uses the step time.

  public static InstantVectorResult rate(RangeVectorResult rv, long rangeMs, EvalContext ctx, long anchorMs) {
    List<SeriesSample> out = new ArrayList<>();
    for (SeriesWindow w : rv.data()) {
      if (!(w.scan() instanceof SumScan) && !(w.scan() instanceof GaugeScan)) {
        continue;
      }
      for (long t = ctx.startMs; t <= ctx.endMs; t += ctx.stepMs) {
        long anchor = anchorMs >= 0 ? anchorMs : t;
        float inc = w.scan() instanceof SumScan ss
            ? sumInWindow(ss, anchor - rangeMs, anchor)
            : sampledCounterIncrease((GaugeScan) w.scan(), anchor - rangeMs, anchor);
        float v = (rangeMs > 0) ? inc / (rangeMs / 1000f) : Float.NaN;
        out.add(new SeriesSample(stripName(w.id()), new Sample(t, v)));
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
        out.add(new SeriesSample(stripName(w.id()), new Sample(t, v)));
      }
    }
    return new InstantVectorResult(out);
  }

  public static InstantVectorResult increase(RangeVectorResult rv, long rangeMs, EvalContext ctx, long anchorMs) {
    List<SeriesSample> out = new ArrayList<>();
    for (SeriesWindow w : rv.data()) {
      if (!(w.scan() instanceof SumScan) && !(w.scan() instanceof GaugeScan)) continue;
      for (long t = ctx.startMs; t <= ctx.endMs; t += ctx.stepMs) {
        long anchor = anchorMs >= 0 ? anchorMs : t;
        float v = w.scan() instanceof SumScan ss
            ? sumInWindow(ss, anchor - rangeMs, anchor)
            : sampledCounterIncrease((GaugeScan) w.scan(), anchor - rangeMs, anchor);
        out.add(new SeriesSample(stripName(w.id()), new Sample(t, v)));
      }
    }
    return new InstantVectorResult(out);
  }

  public static InstantVectorResult delta(RangeVectorResult rv, long rangeMs, EvalContext ctx, long anchorMs) {
    List<SeriesSample> out = new ArrayList<>();
    for (SeriesWindow w : rv.data()) {
      if (!(w.scan() instanceof GaugeScan gs)) continue;
      for (long t = ctx.startMs; t <= ctx.endMs; t += ctx.stepMs) {
        long anchor = anchorMs >= 0 ? anchorMs : t;
        out.add(new SeriesSample(stripName(w.id()), new Sample(t, deltaInWindow(gs, anchor - rangeMs, anchor))));
      }
    }
    return new InstantVectorResult(out);
  }

  public static InstantVectorResult idelta(RangeVectorResult rv, long rangeMs, EvalContext ctx, long anchorMs) {
    List<SeriesSample> out = new ArrayList<>();
    for (SeriesWindow w : rv.data()) {
      if (!(w.scan() instanceof GaugeScan gs)) continue;
      for (long t = ctx.startMs; t <= ctx.endMs; t += ctx.stepMs) {
        long anchor = anchorMs >= 0 ? anchorMs : t;
        out.add(new SeriesSample(stripName(w.id()), new Sample(t, ideltaInWindow(gs, anchor - rangeMs, anchor))));
      }
    }
    return new InstantVectorResult(out);
  }

  public static InstantVectorResult deriv(RangeVectorResult rv, long rangeMs, EvalContext ctx, long anchorMs) {
    List<SeriesSample> out = new ArrayList<>();
    for (SeriesWindow w : rv.data()) {
      if (!(w.scan() instanceof GaugeScan gs)) continue;
      for (long t = ctx.startMs; t <= ctx.endMs; t += ctx.stepMs) {
        long anchor = anchorMs >= 0 ? anchorMs : t;
        out.add(new SeriesSample(stripName(w.id()), new Sample(t, derivInWindow(gs, anchor - rangeMs, anchor))));
      }
    }
    return new InstantVectorResult(out);
  }

  public static InstantVectorResult predictLinear(
      RangeVectorResult rv, long rangeMs, EvalContext ctx, long anchorMs, float t) {
    List<SeriesSample> out = new ArrayList<>();
    for (SeriesWindow w : rv.data()) {
      if (!(w.scan() instanceof GaugeScan gs)) continue;
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
          v = (float) (slope * (anchor / 1000.0 + t) + intercept);
        }
        out.add(new SeriesSample(stripName(w.id()), new Sample(step, v)));
      }
    }
    return new InstantVectorResult(out);
  }

  private static SeriesId stripName(SeriesId id) {
    Map<String, String> tags = new HashMap<>(id.labels().tags());
    tags.remove("__name__");
    return new SeriesId("", new Labels(tags));
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

  private static float sampledCounterIrate(GaugeScan gs, long start, long end) {
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
    var ts = gs.getTimestamps();
    var vals = gs.getValues();
    int firstIdx = -1, lastIdx = -1;
    for (int i = 0; i < ts.size(); i++) {
      long tsi = ts.get(i);
      if (tsi <= start || tsi > end) continue;
      if (firstIdx == -1) firstIdx = i;
      lastIdx = i;
    }
    if (firstIdx == -1) return Float.NaN;
    return vals.get(lastIdx) - vals.get(firstIdx);
  }

  private static float ideltaInWindow(GaugeScan gs, long start, long end) {
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
    float d = vals.get(lastIdx) - vals.get(firstIdx);
    float seconds = Math.max((ts.get(lastIdx) - ts.get(firstIdx)) / 1000f, 1f);
    return d / seconds;
  }
}

/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.eval.ops;

import java.util.*;
import org.okapi.metrics.pojos.results.GaugeScan;
import org.okapi.metrics.pojos.results.SumScan;
import org.okapi.promql.eval.*;
import org.okapi.promql.eval.VectorData.*;

/** Pure functions over RangeVectorResult for counter/gauge transforms. */
public final class RangeFunctions {
  private RangeFunctions() {}

  public static InstantVectorResult rate(RangeVectorResult rv, long rangeMs, EvalContext ctx) {
    List<SeriesSample> out = new ArrayList<>();
    for (SeriesWindow w : rv.data()) {
      if (!(w.scan() instanceof SumScan ss)) continue;
      for (long t = ctx.startMs; t <= ctx.endMs; t += ctx.stepMs) {
        float inc = sumInWindow(ss, t - rangeMs, t);
        float v = (rangeMs > 0) ? inc / (rangeMs / 1000f) : Float.NaN;
        out.add(new SeriesSample(w.id(), new Sample(t, v)));
      }
    }
    return new InstantVectorResult(out);
  }

  public static InstantVectorResult irate(RangeVectorResult rv, long rangeMs, EvalContext ctx) {
    List<SeriesSample> out = new ArrayList<>();
    for (SeriesWindow w : rv.data()) {
      if (!(w.scan() instanceof SumScan ss)) continue;
      for (long t = ctx.startMs; t <= ctx.endMs; t += ctx.stepMs) {
        out.add(new SeriesSample(w.id(), new Sample(t, irateInWindow(ss, t - rangeMs, t))));
      }
    }
    return new InstantVectorResult(out);
  }

  public static InstantVectorResult increase(RangeVectorResult rv, long rangeMs, EvalContext ctx) {
    List<SeriesSample> out = new ArrayList<>();
    for (SeriesWindow w : rv.data()) {
      if (!(w.scan() instanceof SumScan ss)) continue;
      for (long t = ctx.startMs; t <= ctx.endMs; t += ctx.stepMs) {
        out.add(new SeriesSample(w.id(), new Sample(t, sumInWindow(ss, t - rangeMs, t))));
      }
    }
    return new InstantVectorResult(out);
  }

  public static InstantVectorResult delta(RangeVectorResult rv, long rangeMs, EvalContext ctx) {
    List<SeriesSample> out = new ArrayList<>();
    for (SeriesWindow w : rv.data()) {
      if (!(w.scan() instanceof GaugeScan gs)) continue;
      for (long t = ctx.startMs; t <= ctx.endMs; t += ctx.stepMs) {
        out.add(new SeriesSample(w.id(), new Sample(t, deltaInWindow(gs, t - rangeMs, t))));
      }
    }
    return new InstantVectorResult(out);
  }

  public static InstantVectorResult idelta(RangeVectorResult rv, long rangeMs, EvalContext ctx) {
    List<SeriesSample> out = new ArrayList<>();
    for (SeriesWindow w : rv.data()) {
      if (!(w.scan() instanceof GaugeScan gs)) continue;
      for (long t = ctx.startMs; t <= ctx.endMs; t += ctx.stepMs) {
        out.add(new SeriesSample(w.id(), new Sample(t, ideltaInWindow(gs, t - rangeMs, t))));
      }
    }
    return new InstantVectorResult(out);
  }

  public static InstantVectorResult deriv(RangeVectorResult rv, long rangeMs, EvalContext ctx) {
    List<SeriesSample> out = new ArrayList<>();
    for (SeriesWindow w : rv.data()) {
      if (!(w.scan() instanceof GaugeScan gs)) continue;
      for (long t = ctx.startMs; t <= ctx.endMs; t += ctx.stepMs) {
        out.add(new SeriesSample(w.id(), new Sample(t, derivInWindow(gs, t - rangeMs, t))));
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

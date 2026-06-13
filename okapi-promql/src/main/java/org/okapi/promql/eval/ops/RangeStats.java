/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.eval.ops;

import java.util.*;
import org.okapi.metrics.pojos.results.GaugeScan;
import org.okapi.promql.eval.*;
import org.okapi.promql.eval.VectorData.*;

/** Pure functions over RangeVectorResult for window statistics. */
public final class RangeStats {
  private RangeStats() {}

  public static InstantVectorResult avg(RangeVectorResult rv, long rangeMs, EvalContext ctx) {
    return mapWindows(rv, rangeMs, ctx, (ts, vals, winStart, t) -> {
      double sum = 0; int count = 0;
      for (int i = 0; i < ts.size(); i++) {
        if (ts.get(i) <= winStart || ts.get(i) > t) continue;
        sum += vals.get(i); count++;
      }
      return count > 0 ? (float) (sum / count) : Float.NaN;
    });
  }

  public static InstantVectorResult min(RangeVectorResult rv, long rangeMs, EvalContext ctx) {
    return mapWindows(rv, rangeMs, ctx, (ts, vals, winStart, t) -> {
      float min = Float.POSITIVE_INFINITY; int count = 0;
      for (int i = 0; i < ts.size(); i++) {
        if (ts.get(i) <= winStart || ts.get(i) > t) continue;
        if (vals.get(i) < min) min = vals.get(i); count++;
      }
      return count > 0 ? min : Float.NaN;
    });
  }

  public static InstantVectorResult max(RangeVectorResult rv, long rangeMs, EvalContext ctx) {
    return mapWindows(rv, rangeMs, ctx, (ts, vals, winStart, t) -> {
      float max = Float.NEGATIVE_INFINITY; int count = 0;
      for (int i = 0; i < ts.size(); i++) {
        if (ts.get(i) <= winStart || ts.get(i) > t) continue;
        if (vals.get(i) > max) max = vals.get(i); count++;
      }
      return count > 0 ? max : Float.NaN;
    });
  }

  public static InstantVectorResult sum(RangeVectorResult rv, long rangeMs, EvalContext ctx) {
    return mapWindows(rv, rangeMs, ctx, (ts, vals, winStart, t) -> {
      float s = 0;
      for (int i = 0; i < ts.size(); i++) {
        if (ts.get(i) <= winStart || ts.get(i) > t) continue;
        s += vals.get(i);
      }
      return s;
    });
  }

  public static InstantVectorResult count(RangeVectorResult rv, long rangeMs, EvalContext ctx) {
    return mapWindows(rv, rangeMs, ctx, (ts, vals, winStart, t) -> {
      int c = 0;
      for (int i = 0; i < ts.size(); i++) {
        if (ts.get(i) <= winStart || ts.get(i) > t) continue;
        c++;
      }
      return (float) c;
    });
  }

  public static InstantVectorResult last(RangeVectorResult rv, long rangeMs, EvalContext ctx) {
    return mapWindows(rv, rangeMs, ctx, (ts, vals, winStart, t) -> {
      for (int i = ts.size() - 1; i >= 0; --i) {
        if (ts.get(i) <= winStart || ts.get(i) > t) continue;
        return vals.get(i);
      }
      return Float.NaN;
    });
  }

  public static InstantVectorResult present(RangeVectorResult rv, long rangeMs, EvalContext ctx) {
    return mapWindows(rv, rangeMs, ctx, (ts, vals, winStart, t) -> {
      for (int i = 0; i < ts.size(); i++) {
        if (ts.get(i) > winStart && ts.get(i) <= t) return 1f;
      }
      return 0f;
    });
  }

  public static InstantVectorResult quantile(
      float q, RangeVectorResult rv, long rangeMs, EvalContext ctx) {
    return mapWindows(rv, rangeMs, ctx, (ts, vals, winStart, t) -> {
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

  private static InstantVectorResult mapWindows(
      RangeVectorResult rv, long rangeMs, EvalContext ctx, WindowFn fn) {
    List<SeriesSample> out = new ArrayList<>();
    for (SeriesWindow w : rv.data()) {
      if (!(w.scan() instanceof GaugeScan gs)) continue;
      var ts = gs.getTimestamps();
      var vals = gs.getValues();
      for (long t = ctx.startMs; t <= ctx.endMs; t += ctx.stepMs) {
        float v = fn.apply(ts, vals, t - rangeMs, t);
        out.add(new SeriesSample(w.id(), new Sample(t, v)));
      }
    }
    return new InstantVectorResult(out);
  }
}

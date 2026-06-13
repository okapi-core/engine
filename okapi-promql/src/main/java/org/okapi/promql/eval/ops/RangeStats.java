/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.eval.ops;

import org.okapi.metrics.pojos.results.GaugeScan;
import org.okapi.promql.eval.EvalContext;
import org.okapi.promql.eval.InstantVectorResult;
import org.okapi.promql.eval.RangeVectorResult;
import org.okapi.promql.eval.VectorData.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    return mapWindows(rv, rangeMs, ctx, anchorMs, (ts, vals, winStart, t) -> {
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
    return mapWindows(rv, rangeMs, ctx, anchorMs, (ts, vals, winStart, t) -> {
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

  public static InstantVectorResult changes(RangeVectorResult rv, long rangeMs, EvalContext ctx, long anchorMs) {
    return mapWindows(rv, rangeMs, ctx, anchorMs, (ts, vals, winStart, t) -> {
      float count = 0; Float prev = null;
      for (int i = 0; i < ts.size(); i++) {
        if (ts.get(i) <= winStart || ts.get(i) > t) continue;
        if (prev != null && Float.compare(vals.get(i), prev) != 0) count++;
        prev = vals.get(i);
      }
      return count;
    });
  }

  public static InstantVectorResult resets(RangeVectorResult rv, long rangeMs, EvalContext ctx, long anchorMs) {
    return mapWindows(rv, rangeMs, ctx, anchorMs, (ts, vals, winStart, t) -> {
      float count = 0; Float prev = null;
      for (int i = 0; i < ts.size(); i++) {
        if (ts.get(i) <= winStart || ts.get(i) > t) continue;
        if (prev != null && vals.get(i) < prev) count++;
        prev = vals.get(i);
      }
      return count;
    });
  }

  public static InstantVectorResult quantile(
      float q, RangeVectorResult rv, long rangeMs, EvalContext ctx, long anchorMs) {
    return mapWindows(rv, rangeMs, ctx, anchorMs, (ts, vals, winStart, t) -> {
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
    List<SeriesSample> out = new ArrayList<>();
    for (SeriesWindow w : rv.data()) {
      if (!(w.scan() instanceof GaugeScan gs)) continue;
      var ts = gs.getTimestamps();
      var vals = gs.getValues();
      for (long t = ctx.startMs; t <= ctx.endMs; t += ctx.stepMs) {
        long anchor = anchorMs >= 0 ? anchorMs : t;
        float v = fn.apply(ts, vals, anchor - rangeMs, anchor);
        out.add(new SeriesSample(stripName(w.id()), new Sample(t, v)));
      }
    }
    return new InstantVectorResult(out);
  }

  private static SeriesId stripName(SeriesId id) {
    Map<String, String> tags = new HashMap<>(id.labels().tags());
    tags.remove("__name__");
    return new SeriesId("", new Labels(tags));
  }
}

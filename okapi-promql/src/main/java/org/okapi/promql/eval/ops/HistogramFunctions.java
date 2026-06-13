/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.eval.ops;

import java.util.*;
import org.okapi.metrics.pojos.results.HistoScan;
import org.okapi.metrics.pojos.results.HistoScanMerger;
import org.okapi.promql.eval.*;
import org.okapi.promql.eval.HistogramSeries;
import org.okapi.promql.eval.VectorData;
import org.okapi.promql.eval.VectorData.*;

/** Pure functions over histogram range vectors. */
public final class HistogramFunctions {
  private HistogramFunctions() {}

  public static InstantVectorResult quantile(
      float q, RangeVectorResult rv, long rangeMs, EvalContext ctx) {
    List<SeriesSample> out = new ArrayList<>();

    // Group windows by label set (minus 'instance') to merge across instances
    var grouped = new LinkedHashMap<Map<String, String>, List<SeriesWindow>>();
    for (SeriesWindow w : rv.data()) {
      Map<String, String> key = new HashMap<>(w.id().labels().tags());
      key.remove("instance");
      grouped.computeIfAbsent(Collections.unmodifiableMap(key), k -> new ArrayList<>()).add(w);
    }

    for (var e : grouped.entrySet()) {
      VectorData.SeriesId rep = null;
      for (var w : e.getValue()) {
        if (rep == null) rep = new SeriesId(w.id().metric(), new Labels(e.getKey()));
      }
      if (rep == null) continue;

      for (long t = ctx.startMs; t <= ctx.endMs; t += ctx.stepMs) {
        long winStart = t - rangeMs;
        List<HistoScan> toMerge = new ArrayList<>();
        for (var w : e.getValue()) {
          if (w.scan() instanceof HistogramSeries hs) {
            collectPoints(hs, winStart, t, toMerge);
          } else if (w.scan() instanceof HistoScan hs) {
            if (overlaps(hs.getStart(), hs.getEnd(), winStart, t)) toMerge.add(hs);
          }
        }
        if (toMerge.isEmpty()) continue;
        HistoScan merged = HistoScanMerger.merge("", toMerge);
        out.add(new SeriesSample(rep, new Sample(t, quantileFromHistogram(q, merged))));
      }
    }
    return new InstantVectorResult(out);
  }

  static float quantileFromHistogram(double q, HistoScan hs) {
    List<Float> ubs = hs.getUbs();
    List<Integer> counts = hs.getCounts();
    if (counts == null || counts.isEmpty()) return Float.NaN;

    long total = 0;
    for (int c : counts) total += c;
    if (total <= 0) return Float.NaN;

    double target = q * total;
    long cum = 0;
    int k = -1;
    for (int i = 0; i < counts.size(); i++) {
      long next = cum + counts.get(i);
      if (target <= next) { k = i; break; }
      cum = next;
    }
    if (k == -1) k = counts.size() - 1;

    int n = ubs.size();
    float lower, upper;
    if (k == 0) {
      lower = Float.NEGATIVE_INFINITY; upper = ubs.get(0);
    } else if (k < n) {
      lower = ubs.get(k - 1); upper = ubs.get(k);
    } else {
      lower = (n >= 1) ? ubs.get(n - 1) : Float.NEGATIVE_INFINITY;
      upper = Float.POSITIVE_INFINITY;
    }

    int inBucket = counts.get(k);
    if (inBucket <= 0) return Float.isInfinite(upper) ? lower : upper;

    double pos = (target - cum) / Math.max(inBucket, 1);
    if (Float.isInfinite(lower)) return upper;
    if (Float.isInfinite(upper)) return lower;
    return (float) (lower + pos * (upper - lower));
  }

  private static void collectPoints(
      HistogramSeries hs, long winStart, long winEnd, List<HistoScan> out) {
    for (var p : hs.getPoints()) {
      if (!overlaps(p.startMs(), p.endMs(), winStart, winEnd)) continue;
      float[] bounds = p.upperBounds();
      int[] counts = p.counts();
      List<Float> ubs = new ArrayList<>(bounds == null ? 0 : bounds.length);
      if (bounds != null) for (float b : bounds) ubs.add(b);
      List<Integer> cs = new ArrayList<>(counts == null ? 0 : counts.length);
      if (counts != null) for (int c : counts) cs.add(c);
      out.add(new HistoScan("", p.startMs(), p.endMs(), ubs, cs));
    }
  }

  private static boolean overlaps(long startMs, long endMs, long winStart, long winEnd) {
    return startMs <= winEnd && endMs >= winStart;
  }
}

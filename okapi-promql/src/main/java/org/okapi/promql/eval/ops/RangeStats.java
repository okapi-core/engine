/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.eval.ops;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.okapi.metrics.pojos.results.GaugeScan;
import org.okapi.promql.eval.EvalContext;
import org.okapi.promql.eval.HistogramSeries;
import org.okapi.promql.eval.InstantVectorResult;
import org.okapi.promql.eval.RangeEvalContext;
import org.okapi.promql.eval.RangeVectorResult;
import org.okapi.promql.eval.Staleness;
import org.okapi.promql.eval.VectorData.*;

/** Pure functions over RangeVectorResult for window statistics. */
public final class RangeStats {
  public enum TimestampSelector {
    FIRST,
    LAST,
    MIN,
    MAX
  }

  private RangeStats() {}

  public static InstantVectorResult avg(
      RangeVectorResult rv, long rangeMs, EvalContext ctx, long anchorMs) {
    return mapNumericOrHistogramWindows(
        rv,
        rangeMs,
        ctx,
        anchorMs,
        true,
        (ts, vals, winStart, t) -> {
          double sum = 0;
          int count = 0;
          for (int i = 0; i < ts.size(); i++) {
            if (ts.get(i) <= winStart || ts.get(i) > t) continue;
            sum += vals.get(i);
            count++;
          }
          return count > 0 ? (float) (sum / count) : Float.NaN;
        });
  }

  public static InstantVectorResult min(
      RangeVectorResult rv, long rangeMs, EvalContext ctx, long anchorMs) {
    return mapWindows(
        rv,
        rangeMs,
        ctx,
        anchorMs,
        (ts, vals, winStart, t) -> {
          float min = Float.POSITIVE_INFINITY;
          boolean found = false;
          for (int i = 0; i < ts.size(); i++) {
            if (ts.get(i) <= winStart || ts.get(i) > t) continue;
            if (Float.isNaN(vals.get(i))) continue;
            if (vals.get(i) < min) min = vals.get(i);
            found = true;
          }
          return found ? min : Float.NaN;
        });
  }

  public static InstantVectorResult max(
      RangeVectorResult rv, long rangeMs, EvalContext ctx, long anchorMs) {
    return mapWindows(
        rv,
        rangeMs,
        ctx,
        anchorMs,
        (ts, vals, winStart, t) -> {
          float max = Float.NEGATIVE_INFINITY;
          boolean found = false;
          for (int i = 0; i < ts.size(); i++) {
            if (ts.get(i) <= winStart || ts.get(i) > t) continue;
            if (Float.isNaN(vals.get(i))) continue;
            if (vals.get(i) > max) max = vals.get(i);
            found = true;
          }
          return found ? max : Float.NaN;
        });
  }

  public static InstantVectorResult sum(
      RangeVectorResult rv, long rangeMs, EvalContext ctx, long anchorMs) {
    return mapNumericOrHistogramWindows(
        rv,
        rangeMs,
        ctx,
        anchorMs,
        false,
        (ts, vals, winStart, t) -> {
          float s = 0;
          for (int i = 0; i < ts.size(); i++) {
            if (ts.get(i) <= winStart || ts.get(i) > t) continue;
            s += vals.get(i);
          }
          return s;
        });
  }

  public static InstantVectorResult count(
      RangeVectorResult rv, long rangeMs, EvalContext ctx, long anchorMs) {
    List<SeriesSample> out = new ArrayList<>();
    for (SeriesWindow window : rv.data()) {
      for (long t = ctx.startMs; t <= ctx.endMs; t += ctx.stepMs) {
        long anchor = anchorMs >= 0 ? anchorMs : t;
        var samples = samplesInWindow(window, anchor - rangeMs, anchor);
        if (!samples.isEmpty()) {
          out.add(new SeriesSample(SeriesIds.derived(window.id()), new Sample(t, samples.size())));
        }
      }
    }
    return new InstantVectorResult(out);
  }

  public static InstantVectorResult last(
      RangeVectorResult rv, long rangeMs, EvalContext ctx, long anchorMs) {
    return selectSample(rv, rangeMs, ctx, anchorMs, false);
  }

  public static InstantVectorResult present(
      RangeVectorResult rv, long rangeMs, EvalContext ctx, long anchorMs) {
    List<SeriesSample> out = new ArrayList<>();
    for (SeriesWindow window : rv.data()) {
      for (long t = ctx.startMs; t <= ctx.endMs; t += ctx.stepMs) {
        long anchor = anchorMs >= 0 ? anchorMs : t;
        if (!samplesInWindow(window, anchor - rangeMs, anchor).isEmpty()) {
          out.add(new SeriesSample(SeriesIds.derived(window.id()), new Sample(t, 1f)));
        }
      }
    }
    return new InstantVectorResult(out);
  }

  public static InstantVectorResult first(
      RangeVectorResult rv, long rangeMs, EvalContext ctx, long anchorMs) {
    return selectSample(rv, rangeMs, ctx, anchorMs, true);
  }

  public static InstantVectorResult absent(
      RangeVectorResult rv,
      long rangeMs,
      EvalContext ctx,
      long anchorMs,
      Map<String, String> labels) {
    List<SeriesSample> out = new ArrayList<>();
    for (long t = ctx.startMs; t <= ctx.endMs; t += ctx.stepMs) {
      long anchor = anchorMs >= 0 ? anchorMs : t;
      boolean hasSamples = false;
      for (SeriesWindow window : rv.data()) {
        if (!samplesInWindow(window, anchor - rangeMs, anchor).isEmpty()) {
          hasSamples = true;
          break;
        }
      }
      if (!hasSamples) {
        out.add(new SeriesSample(new SeriesId("", new Labels(labels)), new Sample(t, 1f)));
      }
    }
    return new InstantVectorResult(out);
  }

  public static InstantVectorResult timestampOf(
      RangeVectorResult rv,
      long rangeMs,
      EvalContext ctx,
      long anchorMs,
      TimestampSelector selector) {
    List<SeriesSample> out = new ArrayList<>();
    for (SeriesWindow window : rv.data()) {
      for (long t = ctx.startMs; t <= ctx.endMs; t += ctx.stepMs) {
        long anchor = anchorMs >= 0 ? anchorMs : t;
        TimelineSample selected =
            selectTimelineSample(samplesInWindow(window, anchor - rangeMs, anchor), selector);
        if (selected != null) {
          out.add(
              new SeriesSample(
                  SeriesIds.derived(window.id()), new Sample(t, selected.sourceTs() / 1000f)));
        }
      }
    }
    return new InstantVectorResult(out);
  }

  public static InstantVectorResult stddev(
      RangeVectorResult rv, long rangeMs, EvalContext ctx, long anchorMs) {
    return mapWindows(
        rv,
        rangeMs,
        ctx,
        anchorMs,
        (ts, vals, winStart, t) -> {
          double sum = 0;
          int count = 0;
          for (int i = 0; i < ts.size(); i++) {
            if (ts.get(i) <= winStart || ts.get(i) > t) continue;
            sum += vals.get(i);
            count++;
          }
          if (count == 0) return Float.NaN;
          double mean = sum / count;
          double var = 0;
          for (int i = 0; i < ts.size(); i++) {
            if (ts.get(i) <= winStart || ts.get(i) > t) continue;
            double d = vals.get(i) - mean;
            var += d * d;
          }
          return (float) Math.sqrt(var / count);
        });
  }

  public static InstantVectorResult stdvar(
      RangeVectorResult rv, long rangeMs, EvalContext ctx, long anchorMs) {
    return mapWindows(
        rv,
        rangeMs,
        ctx,
        anchorMs,
        (ts, vals, winStart, t) -> {
          double sum = 0;
          int count = 0;
          for (int i = 0; i < ts.size(); i++) {
            if (ts.get(i) <= winStart || ts.get(i) > t) continue;
            sum += vals.get(i);
            count++;
          }
          if (count == 0) return Float.NaN;
          double mean = sum / count;
          double var = 0;
          for (int i = 0; i < ts.size(); i++) {
            if (ts.get(i) <= winStart || ts.get(i) > t) continue;
            double d = vals.get(i) - mean;
            var += d * d;
          }
          return (float) (var / count);
        });
  }

  public static InstantVectorResult mad(
      RangeVectorResult rv, long rangeMs, EvalContext ctx, long anchorMs) {
    return mapWindows(
        rv,
        rangeMs,
        ctx,
        anchorMs,
        (ts, vals, winStart, t) -> {
          List<Float> window = new ArrayList<>();
          for (int i = 0; i < ts.size(); i++) {
            if (ts.get(i) <= winStart || ts.get(i) > t) continue;
            window.add(vals.get(i));
          }
          if (window.isEmpty()) return Float.NaN;
          window.sort(Float::compare);
          float median =
              window.size() % 2 == 1
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

  public static InstantVectorResult changes(
      RangeVectorResult rv, RangeEvalContext rangeCtx, long anchorMs) {
    return mapSeriesWindows(
        rv,
        rangeCtx,
        anchorMs,
        points -> {
          float count = 0;
          SeriesPoint prev = null;
          for (SeriesPoint point : points) {
            if (prev != null && !sameValue(prev.value(), point.value())) count++;
            prev = point;
          }
          return count;
        });
  }

  public static InstantVectorResult resets(
      RangeVectorResult rv, RangeEvalContext rangeCtx, long anchorMs) {
    return mapSeriesWindows(
        rv,
        rangeCtx,
        anchorMs,
        points -> {
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
        if (!Staleness.isStale(point.value()))
          points.add(new SeriesPoint(point.ts(), point.value()));
      return points;
    }
    if (window.scan() instanceof HistogramSeries series) {
      List<SeriesPoint> points = new ArrayList<>();
      for (var point : series.getPoints()) {
        if (!rangeCtx.includes(point.endMs(), anchor)) continue;
        if (point instanceof HistogramSeries.FloatSample sample) {
          if (!Staleness.isStale(sample.value()))
            points.add(new SeriesPoint(point.endMs(), sample.value()));
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
    if (left instanceof HistogramSeries.HistogramSample a
        && right instanceof HistogramSeries.HistogramSample b)
      return HistogramSeries.sameValue(a, b);
    return left.equals(right);
  }

  private static boolean isReset(Object previous, Object current) {
    if (previous instanceof Float a && current instanceof Float b) return b < a;
    if (previous instanceof HistogramSeries.HistogramSample a
        && current instanceof HistogramSeries.HistogramSample b)
      return HistogramSeries.isReset(a, b);
    return !previous.getClass().equals(current.getClass());
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
    return mapWindows(
        rv,
        rangeMs,
        ctx,
        anchorMs,
        (ts, vals, winStart, t) -> {
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

  private static InstantVectorResult mapNumericOrHistogramWindows(
      RangeVectorResult rv,
      long rangeMs,
      EvalContext ctx,
      long anchorMs,
      boolean average,
      WindowFn floatFn) {
    List<SeriesSample> out = new ArrayList<>();
    for (SeriesWindow window : rv.data()) {
      for (long t = ctx.startMs; t <= ctx.endMs; t += ctx.stepMs) {
        long anchor = anchorMs >= 0 ? anchorMs : t;
        var samples = samplesInWindow(window, anchor - rangeMs, anchor);
        boolean hasFloats = samples.stream().anyMatch(sample -> sample.histogram() == null);
        boolean hasHistograms = samples.stream().anyMatch(sample -> sample.histogram() != null);
        if (hasFloats && hasHistograms) continue;
        if (hasHistograms) {
          HistogramSeries.HistogramSample histogram = null;
          for (var sample : samples) {
            if (histogram != null
                && !HistogramSeries.compatibleRepresentation(histogram, sample.histogram())) {
              histogram = null;
              break;
            }
            histogram =
                histogram == null
                    ? sample.histogram()
                    : HistogramSeries.add(histogram, sample.histogram());
          }
          if (histogram == null) continue;
          if (average) histogram = HistogramSeries.scale(histogram, 1d / samples.size());
          out.add(new SeriesSample(SeriesIds.derived(window.id()), new Sample(t, t, histogram)));
          continue;
        }
        GaugeScan scan = floatScan(window);
        if (scan == null || scan.getTimestamps().isEmpty()) continue;
        var normalized = Staleness.withoutStaleSamples(scan);
        float value =
            floatFn.apply(
                normalized.getTimestamps(), normalized.getValues(), anchor - rangeMs, anchor);
        out.add(new SeriesSample(SeriesIds.derived(window.id()), new Sample(t, value)));
      }
    }
    return new InstantVectorResult(out);
  }

  private static InstantVectorResult mapWindows(
      RangeVectorResult rv,
      long rangeMs,
      EvalContext ctx,
      long anchorMs,
      WindowFn fn,
      boolean derived) {
    List<SeriesSample> out = new ArrayList<>();
    for (SeriesWindow w : rv.data()) {
      GaugeScan gs = floatScan(w);
      if (gs == null || gs.getTimestamps().isEmpty()) continue;
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

  private static GaugeScan floatScan(SeriesWindow window) {
    if (window.scan() instanceof GaugeScan scan) return scan;
    if (window.scan() instanceof HistogramSeries series) return series.floatScan();
    return null;
  }

  private static InstantVectorResult selectSample(
      RangeVectorResult rv, long rangeMs, EvalContext ctx, long anchorMs, boolean first) {
    List<SeriesSample> out = new ArrayList<>();
    for (SeriesWindow window : rv.data()) {
      for (long t = ctx.startMs; t <= ctx.endMs; t += ctx.stepMs) {
        long anchor = anchorMs >= 0 ? anchorMs : t;
        var samples = samplesInWindow(window, anchor - rangeMs, anchor);
        if (samples.isEmpty()) continue;
        var selected = samples.get(first ? 0 : samples.size() - 1);
        out.add(new SeriesSample(window.id(), selected.at(t)));
      }
    }
    return new InstantVectorResult(out);
  }

  private static List<TimelineSample> samplesInWindow(SeriesWindow window, long start, long end) {
    List<TimelineSample> samples = new ArrayList<>();
    if (window.scan() instanceof GaugeScan scan) {
      var normalized = Staleness.withoutStaleSamples(scan);
      for (int i = 0; i < normalized.getTimestamps().size(); i++) {
        long timestamp = normalized.getTimestamps().get(i);
        if (timestamp > start && timestamp <= end) {
          samples.add(new TimelineSample(timestamp, normalized.getValues().get(i), null));
        }
      }
    } else if (window.scan() instanceof HistogramSeries series) {
      for (var point : series.getPoints()) {
        if (point.endMs() <= start || point.endMs() > end) continue;
        if (point instanceof HistogramSeries.FloatSample sample) {
          if (!Staleness.isStale(sample.value())) {
            samples.add(new TimelineSample(point.endMs(), sample.value(), null));
          }
        } else if (point instanceof HistogramSeries.HistogramSample histogram) {
          samples.add(new TimelineSample(point.endMs(), null, histogram));
        }
      }
    }
    return samples;
  }

  private static TimelineSample selectTimelineSample(
      List<TimelineSample> samples, TimestampSelector selector) {
    if (samples.isEmpty()) return null;
    if (selector == TimestampSelector.FIRST) return samples.get(0);
    if (selector == TimestampSelector.LAST) return samples.get(samples.size() - 1);

    TimelineSample selected = null;
    for (TimelineSample sample : samples) {
      if (sample.value() == null || Float.isNaN(sample.value())) continue;
      if (selected == null
          || selector == TimestampSelector.MIN && sample.value() <= selected.value()
          || selector == TimestampSelector.MAX && sample.value() >= selected.value()) {
        selected = sample;
      }
    }
    return selected;
  }

  private record TimelineSample(
      long sourceTs, Float value, HistogramSeries.HistogramSample histogram) {
    private Sample at(long timestamp) {
      return histogram == null
          ? new Sample(timestamp, sourceTs, value)
          : new Sample(timestamp, sourceTs, histogram);
    }
  }
}

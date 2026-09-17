/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.eval;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.apache.commons.math3.util.FastMath;
import org.okapi.metrics.pojos.results.GaugeScan;
import org.okapi.metrics.pojos.results.Scan;
import org.okapi.metrics.pojos.results.SumScan;
import org.okapi.promql.eval.VectorData.*;
import org.okapi.promql.eval.exceptions.EvaluationException;
import org.okapi.promql.eval.nodes.*;
import org.okapi.promql.eval.ops.*;
import org.okapi.promql.parse.LabelMatcher;

public final class NodeEvaluator {

  private static final long STALENESS_MS = 5 * 60_000L;

  public ExpressionResult eval(LogicalExpr expr, EvalContext ctx) throws EvaluationException {
    return switch (expr) {
      case LiteralExpr e -> new ScalarResult(e.value);
      case SelectorExpr e -> evalSelector(e, ctx);
      case InstantizeExpr e -> evalInstantize(e, ctx);
      case RangeSelectorExpr e -> evalRangeSelector(e, ctx);
      case SmoothedExpr e -> evalSmoothed(e, ctx);
      case BinaryOpExpr e -> evalBinaryOp(e, ctx);
      case AggregateExpr e -> evalAggregate(e, ctx);
      case FunctionExpr e -> evalFunction(e, ctx);
      case AtExpr e -> evalAt(e, ctx);
      case OffsetExpr e -> evalOffset(e, ctx);
      case SubqueryExpr e -> evalSubquery(e, ctx);
      case StringLiteralExpr e -> new StringResult(e.value);
    };
  }

  // ---------- Selector ----------

  private ExpressionResult evalSelector(SelectorExpr e, EvalContext ctx) {
    long start = ctx.startMs, end = ctx.endMs;
    if (e.atTsMs != null) {
      start = end = e.atTsMs;
    }
    if (e.offset != null) {
      long offset = e.offset.evalMs(ctx);
      start -= offset;
      end -= offset;
    }
    var series = ctx.discovery.expand(e.metricOrNull, e.matchers, start, end);
    List<SeriesWindow> windows = new ArrayList<>(series.size());
    for (SeriesId id : series) {
      Scan scan = ctx.client.get(id.metric(), id.labels().tags(), ctx.resolution, start, end);
      if (!isEmptyScan(scan)) windows.add(new SeriesWindow(id, scan));
    }
    return new RangeVectorResult(windows);
  }

  private ExpressionResult evalInstantize(InstantizeExpr e, EvalContext ctx)
      throws EvaluationException {
    // Expand fetch window to include the staleness lookback so the selector retrieves
    // data points that pre-date startMs but still fall within the 5-minute staleness window.
    var fetchCtx = ctx.withWindow(Math.max(0L, ctx.startMs - STALENESS_MS), ctx.endMs);
    var inner = resolveSelectorOffset(e.inner, ctx);
    var res = eval(inner, fetchCtx);
    if (!(res instanceof RangeVectorResult rv)) return res;

    List<SeriesSample> out = new ArrayList<>();
    for (SeriesWindow w : rv.data()) {
      if (w.scan() instanceof GaugeScan gs) {
        appendGaugeSamples(w.id(), gs, inner, ctx, out);
      } else if (w.scan() instanceof HistogramSeries hs) {
        appendHistogramSamples(w.id(), hs, inner, ctx, out);
      }
    }
    return new InstantVectorResult(out);
  }

  private void appendGaugeSamples(
      SeriesId id, GaugeScan scan, LogicalExpr inner, EvalContext ctx, List<SeriesSample> out) {
    var tsList = scan.getTimestamps();
    var valList = scan.getValues();
    int n = tsList.size();
    int idx = 0;
    for (long t = ctx.startMs; t <= ctx.endMs; t += ctx.stepMs) {
      // When the inner selector has an embedded @ or offset, the effective lookup
      // time differs from the outer step time t. Use it for the staleness window.
      long effT = effectiveStepTime(inner, t, ctx);
      long winStart = effT - STALENESS_MS;
      while (idx + 1 < n && tsList.get(idx + 1) <= effT) idx++;
      if (n == 0) continue;
      long ptsTs = tsList.get(idx);
      if (ptsTs <= effT && ptsTs > winStart && !Staleness.isStale(valList.get(idx)))
        out.add(new SeriesSample(id, new Sample(t, ptsTs, valList.get(idx))));
    }
  }

  private void appendHistogramSamples(
      SeriesId id,
      HistogramSeries series,
      LogicalExpr inner,
      EvalContext ctx,
      List<SeriesSample> out) {
    var points = series.getPoints();
    int n = points.size();
    int idx = 0;
    for (long t = ctx.startMs; t <= ctx.endMs; t += ctx.stepMs) {
      long effT = effectiveStepTime(inner, t, ctx);
      long winStart = effT - STALENESS_MS;
      while (idx + 1 < n && points.get(idx + 1).endMs() <= effT) idx++;
      if (n == 0) continue;
      var point = points.get(idx);
      if (point.endMs() <= effT && point.endMs() > winStart) {
        if (point instanceof HistogramSeries.HistogramSample histogram) {
          out.add(new SeriesSample(id, new Sample(t, point.endMs(), histogram)));
        } else if (point instanceof HistogramSeries.FloatSample sample
            && !Staleness.isStale(sample.value())) {
          out.add(new SeriesSample(id, new Sample(t, point.endMs(), sample.value())));
        }
      }
    }
  }

  private LogicalExpr resolveSelectorOffset(LogicalExpr inner, EvalContext ctx) {
    if (inner instanceof SelectorExpr s && s.offset != null) {
      return new SelectorExpr(
          s.metricOrNull, s.matchers, s.atTsMs, DurationExpr.fixedMs(s.offset.evalMs(ctx)));
    }
    return inner;
  }

  private long effectiveStepTime(LogicalExpr inner, long outerT, EvalContext ctx) {
    if (inner instanceof SelectorExpr s) {
      long base = s.atTsMs != null ? s.atTsMs : outerT;
      long off = s.offset != null ? s.offset.evalMs(ctx) : 0L;
      return base - off;
    }
    return outerT;
  }

  // ---------- Range selector ----------

  private ExpressionResult evalRangeSelector(RangeSelectorExpr e, EvalContext ctx)
      throws EvaluationException {
    long range = e.range.evalMs(ctx);
    if (range < 0) throw new EvaluationException("range selector duration must not be negative");
    long start = ctx.startMs - range;
    long end = ctx.endMs;
    if (e.mode == ExtendedVectorMode.ANCHORED) {
      start = Math.max(0L, start - STALENESS_MS + 1);
    } else if (e.mode == ExtendedVectorMode.SMOOTHED) {
      start = Math.max(0L, start - STALENESS_MS + 1);
      end += STALENESS_MS;
    }
    Long offset = e.offset == null ? null : e.offset.evalMs(ctx);
    if (offset != null) {
      start -= offset;
      end -= offset;
    }
    var base =
        new SelectorExpr(e.base.metricOrNull, e.base.matchers, e.base.atTsMs, (DurationExpr) null);
    var rv = (RangeVectorResult) evalSelector(base, ctx.withWindow(start, end));

    // When an offset is applied, the raw data timestamps are in shifted time. Advance them
    // by offsetMs so downstream window functions compute the correct (t - rangeMs, t] bounds
    // against the original (unshifted) evaluation times.
    if (offset == null) return rv;
    List<SeriesWindow> shifted = new ArrayList<>(rv.data().size());
    for (SeriesWindow w : rv.data()) {
      if (w.scan() instanceof GaugeScan gs) {
        List<Long> ts = new ArrayList<>(gs.getTimestamps().size());
        for (Long t : gs.getTimestamps()) ts.add(t + offset);
        shifted.add(
            new SeriesWindow(
                w.id(),
                GaugeScan.builder()
                    .universalPath(gs.getUniversalPath())
                    .timestamps(Collections.unmodifiableList(ts))
                    .values(gs.getValues())
                    .build()));
      } else if (w.scan() instanceof SumScan ss) {
        List<Long> ts = new ArrayList<>(ss.getTs().size());
        for (Long t : ss.getTs()) ts.add(t + offset);
        shifted.add(
            new SeriesWindow(
                w.id(),
                SumScan.builder()
                    .universalPath(ss.getUniversalPath())
                    .ts(Collections.unmodifiableList(ts))
                    .windowSize(ss.getWindowSize())
                    .counts(ss.getCounts())
                    .build()));
      } else if (w.scan() instanceof HistogramSeries hs) {
        List<HistogramSeries.SeriesSample> points = new ArrayList<>(hs.getPoints().size());
        for (var point : hs.getPoints()) points.add(retime(point, point.endMs() + offset));
        shifted.add(new SeriesWindow(w.id(), new HistogramSeries(hs.getUniversalPath(), points)));
      } else {
        shifted.add(w);
      }
    }
    return new RangeVectorResult(shifted);
  }

  private ExpressionResult evalSmoothed(SmoothedExpr e, EvalContext ctx) {
    if (!(e.inner() instanceof InstantizeExpr instantize)
        || !(instantize.inner instanceof SelectorExpr selector)) {
      throw new EvaluationException("smoothed modifier can only be used with an instant selector");
    }
    var fetchCtx =
        ctx.withWindow(Math.max(0L, ctx.startMs - STALENESS_MS), ctx.endMs + STALENESS_MS);
    var rv = (RangeVectorResult) evalSelector(selector, fetchCtx);
    List<SeriesSample> out = new ArrayList<>();
    for (SeriesWindow window : rv.data()) {
      if (!(window.scan() instanceof GaugeScan scan)) continue;
      for (long t = ctx.startMs; t <= ctx.endMs; t += ctx.stepMs) {
        Double value = interpolateGauge(scan, t);
        if (value != null) out.add(new SeriesSample(window.id(), new Sample(t, value)));
      }
    }
    return new InstantVectorResult(out);
  }

  private Double interpolateGauge(GaugeScan scan, long target) {
    var ts = scan.getTimestamps();
    var vals = scan.getValues();
    if (ts.isEmpty()) return null;
    int after = 0;
    while (after < ts.size() && ts.get(after) < target) after++;
    if (after < ts.size() && ts.get(after) == target) return vals.get(after).doubleValue();
    if (after == 0) return vals.get(0).doubleValue();
    if (after == ts.size()) return vals.get(vals.size() - 1).doubleValue();
    int before = after - 1;
    double ratio = (double) (target - ts.get(before)) / (ts.get(after) - ts.get(before));
    return (vals.get(before) + ratio * (vals.get(after) - vals.get(before)));
  }

  private boolean isEmptyScan(Scan scan) {
    if (scan instanceof GaugeScan gs) return gs.getTimestamps().isEmpty();
    if (scan instanceof SumScan ss) return ss.getTs().isEmpty();
    if (scan instanceof HistogramSeries hs) return hs.getPoints().isEmpty();
    return false;
  }

  // ---------- At / Offset ----------

  private ExpressionResult evalAt(AtExpr e, EvalContext ctx) throws EvaluationException {
    var s = TypeChecks.requireScalar(eval(e.atScalar, ctx), "@");
    long tsMs = (long) (s.value * 1000L);
    var result = eval(e.inner, ctx.withWindow(tsMs, tsMs));
    if (!(result instanceof InstantVectorResult iv)) return result;
    List<SeriesSample> out = new ArrayList<>();
    for (var sample : iv.data())
      for (long t = ctx.startMs; t <= ctx.endMs; t += ctx.stepMs)
        out.add(
            new SeriesSample(
                sample.series(),
                new Sample(t, sample.sample().sourceTs(), sample.sample().value())));
    return new InstantVectorResult(out);
  }

  private ExpressionResult evalOffset(OffsetExpr e, EvalContext ctx) throws EvaluationException {
    long offset = e.offset.evalMs(ctx);
    return eval(e.inner, ctx.withWindow(ctx.startMs - offset, ctx.endMs - offset));
  }

  // ---------- Subquery ----------

  private ExpressionResult evalSubquery(SubqueryExpr e, EvalContext ctx)
      throws EvaluationException {
    long range = e.range.evalMs(ctx);
    long step = e.step.evalMs(ctx);
    long offset = e.offset == null ? 0L : e.offset.evalMs(ctx);
    if (range < 0) throw new EvaluationException("subquery range must not be negative");
    if (step <= 0) throw new EvaluationException("subquery step must be positive");
    long subStart = ceilToStep(ctx.startMs - range - offset, step);
    long subEnd = floorToStep(ctx.endMs - offset, step);
    var subCtx = ctx.withWindow(subStart, subEnd, step);
    var innerRes = eval(e.inner, subCtx);

    if (innerRes instanceof InstantVectorResult iv) {
      Map<SeriesId, List<Sample>> map = new LinkedHashMap<>();
      for (SeriesSample s : iv.data())
        map.computeIfAbsent(s.series(), k -> new ArrayList<>()).add(s.sample());

      List<SeriesWindow> out = new ArrayList<>(map.size());
      for (var entry : map.entrySet()) {
        var samples = entry.getValue();
        samples.sort(Comparator.comparingLong(Sample::ts));
        if (samples.stream().anyMatch(Sample::isHistogram)) {
          List<HistogramSeries.SeriesSample> points = new ArrayList<>(samples.size());
          for (var sample : samples) {
            long ts = sample.ts() + offset;
            points.add(
                sample.isHistogram()
                    ? retime(sample.histogram(), ts)
                    : new HistogramSeries.FloatSample(ts, ts, (float) sample.value()));
          }
          out.add(new SeriesWindow(entry.getKey(), new HistogramSeries("", points)));
          continue;
        }
        List<Long> ts = new ArrayList<>(samples.size());
        List<Float> vals = new ArrayList<>(samples.size());
        for (var smp : samples) {
          ts.add(smp.ts() + offset);
          vals.add((float) smp.value());
        }
        GaugeScan gs =
            GaugeScan.builder()
                .universalPath("")
                .timestamps(Collections.unmodifiableList(ts))
                .values(Collections.unmodifiableList(vals))
                .build();
        out.add(new SeriesWindow(entry.getKey(), gs));
      }
      return new RangeVectorResult(out);
    }

    if (innerRes instanceof RangeVectorResult rv) {
      if (offset == 0) return rv;
      List<SeriesWindow> shifted = new ArrayList<>(rv.data().size());
      for (SeriesWindow w : rv.data()) {
        if (w.scan() instanceof GaugeScan gs) {
          List<Long> ts = new ArrayList<>(gs.getTimestamps().size());
          for (Long t : gs.getTimestamps()) ts.add(t + offset);
          GaugeScan shiftedGs =
              GaugeScan.builder()
                  .universalPath(gs.getUniversalPath())
                  .timestamps(Collections.unmodifiableList(ts))
                  .values(gs.getValues())
                  .build();
          shifted.add(new SeriesWindow(w.id(), shiftedGs));
        } else if (w.scan() instanceof HistogramSeries hs) {
          List<HistogramSeries.SeriesSample> points = new ArrayList<>(hs.getPoints().size());
          for (var point : hs.getPoints()) points.add(retime(point, point.endMs() + offset));
          shifted.add(new SeriesWindow(w.id(), new HistogramSeries(hs.getUniversalPath(), points)));
        } else {
          shifted.add(w);
        }
      }
      return new RangeVectorResult(shifted);
    }

    return innerRes;
  }

  private HistogramSeries.SeriesSample retime(HistogramSeries.SeriesSample point, long ts) {
    if (point instanceof HistogramSeries.FloatSample sample)
      return new HistogramSeries.FloatSample(ts, ts, sample.value());
    if (point instanceof HistogramSeries.NativeHistogramSample sample)
      return new HistogramSeries.NativeHistogramSample(
          ts,
          ts,
          sample.schema(),
          sample.zeroThreshold(),
          sample.zeroCount(),
          sample.positiveOffset(),
          sample.positiveBuckets(),
          sample.negativeOffset(),
          sample.negativeBuckets(),
          sample.customValues(),
          sample.sum(),
          sample.count(),
          sample.counterResetHint());
    var sample = (HistogramSeries.ExplicitHistogramSample) point;
    return new HistogramSeries.ExplicitHistogramSample(
        ts,
        ts,
        sample.temporality(),
        sample.upperBounds(),
        sample.counts(),
        sample.sum(),
        sample.count());
  }

  // ---------- Binary op ----------

  private ExpressionResult evalBinaryOp(BinaryOpExpr e, EvalContext ctx)
      throws EvaluationException {
    var l = eval(e.left, ctx);
    var r = eval(e.right, ctx);

    if (l instanceof ScalarResult ls && r instanceof ScalarResult rs)
      return evalScalarScalar(e, ls.value, rs.value);

    if (l instanceof ScalarResult ls && r instanceof InstantVectorResult rv)
      return evalScalarVector(e, ls.value, rv);

    if (l instanceof InstantVectorResult lv && r instanceof ScalarResult rs)
      return evalVectorScalar(e, lv, rs.value);

    if (l instanceof InstantVectorResult lv && r instanceof InstantVectorResult rv)
      return evalVectorVector(e, lv, rv);

    throw new EvaluationException("unsupported operand types for " + e.op);
  }

  private ExpressionResult evalScalarScalar(BinaryOpExpr e, double a, double b) {
    if (isArithmetic(e.op)) return new ScalarResult(arithmethicOp(a, b, e.op));
    if (isComparison(e.op)) {
      boolean ok = compare(a, b, e.op);
      return e.boolModifier
          ? new ScalarResult(ok ? 1f : 0f)
          : (ok ? new ScalarResult(a) : new ScalarResult(Double.NaN));
    }
    throw new EvaluationException("set operators require instant vectors, not scalars");
  }

  private InstantVectorResult evalScalarVector(BinaryOpExpr e, double s, InstantVectorResult v) {
    if (isArithmetic(e.op))
      return mapVectorArithmetic(v, e.op, val -> arithmethicOp(s, val, e.op), s, true);
    if (isComparison(e.op)) {
      return e.boolModifier
          ? mapVectorDropName(v, val -> compare(s, val, e.op) ? 1d : 0d)
          : filterVector(v, val -> compare(s, val, e.op));
    }
    throw new EvaluationException("set operators require instant vectors, not scalars");
  }

  private InstantVectorResult evalVectorScalar(BinaryOpExpr e, InstantVectorResult v, double s) {
    if (e.op.equals("</") || e.op.equals(">/"))
      return HistogramFunctions.trim(v, s, e.op.equals(">/"));
    if (isArithmetic(e.op))
      return mapVectorArithmetic(v, e.op, val -> arithmethicOp(val, s, e.op), s, false);
    if (isComparison(e.op)) {
      return e.boolModifier
          ? mapVectorDropName(v, val -> compare(val, s, e.op) ? 1d : 0d)
          : filterVector(v, val -> compare(val, s, e.op));
    }
    throw new EvaluationException("set operators require instant vectors, not scalars");
  }

  private ExpressionResult evalVectorVector(
      BinaryOpExpr e, InstantVectorResult left, InstantVectorResult right)
      throws EvaluationException {
    String op = e.op.toLowerCase(Locale.ROOT);
    var ms = e.matchSpec;

    var ignoredMetadata = metadataAbsentFromEitherSide(left.data(), right.data());
    var leftIdx = indexByJoinKey(left.data(), ms, ignoredMetadata);
    var rightIdx = indexByJoinKey(right.data(), ms, ignoredMetadata);
    List<SeriesSample> out = new ArrayList<>();

    switch (op) {
      case "and" -> {
        for (var key : leftIdx.keySet()) {
          var rList = rightIdx.get(key);
          if (rList != null && !rList.isEmpty()) out.addAll(leftIdx.get(key));
        }
        return new InstantVectorResult(out);
      }
      case "or" -> {
        // LHS first; then RHS samples whose join key has no LHS entry
        Set<JoinKey> lhsKeys = leftIdx.keySet();
        for (var s : left.data()) out.add(s);
        for (var key : rightIdx.keySet()) {
          if (!lhsKeys.contains(key)) out.addAll(rightIdx.get(key));
        }
        return new InstantVectorResult(out);
      }
      case "unless" -> {
        for (var key : leftIdx.keySet()) {
          var rList = rightIdx.get(key);
          if (rList == null || rList.isEmpty()) out.addAll(leftIdx.get(key));
        }
        return new InstantVectorResult(out);
      }
      default -> {
        boolean isCmp = isComparison(op);
        boolean isAr = isArithmetic(op);
        boolean isTrim = op.equals("</") || op.equals(">/");
        if (!isCmp && !isAr && !isTrim) throw new EvaluationException("unsupported op: " + op);

        boolean groupLeft = ms != null && ms.groupLeft;
        boolean groupRight = ms != null && ms.groupRight;
        if (groupLeft && groupRight)
          throw new EvaluationException("cannot specify both group_left and group_right");

        for (var key : leftIdx.keySet()) {
          var lList = leftIdx.get(key);
          var rList = rightIdx.get(key);
          if (rList == null || rList.isEmpty()) {
            Double fill = fillForMissingRight(e, groupRight);
            if (fill != null) {
              for (var l : lList)
                combine(e, l, filledSample(l, fill, groupRight ? ms : null), isCmp)
                    .ifPresent(out::add);
            }
            continue;
          }

          if (!groupLeft && !groupRight && (lList.size() != 1 || rList.size() != 1))
            throw new EvaluationException(
                "many-to-many matching not allowed without group_left/group_right");
          if (groupLeft && rList.size() != 1)
            throw new EvaluationException("group_left requires exactly one RHS match per key");
          if (groupRight && lList.size() != 1)
            throw new EvaluationException("group_right requires exactly one LHS match per key");

          if (groupLeft) {
            var r = rList.get(0);
            for (var l : lList) combine(e, l, r, isCmp).ifPresent(out::add);
          } else if (groupRight) {
            var l = lList.get(0);
            for (var r : rList) combine(e, l, r, isCmp).ifPresent(out::add);
          } else {
            combine(e, lList.get(0), rList.get(0), isCmp).ifPresent(out::add);
          }
        }
        Double fill = fillForMissingLeft(e, groupRight);
        if (fill != null) {
          for (var key : rightIdx.keySet()) {
            if (leftIdx.containsKey(key)) continue;
            for (var r : rightIdx.get(key))
              combine(e, filledSample(r, fill, groupLeft ? ms : null), r, isCmp)
                  .ifPresent(out::add);
          }
        }
        return new InstantVectorResult(out);
      }
    }
  }

  private Optional<SeriesSample> combine(
      BinaryOpExpr e, SeriesSample l, SeriesSample r, boolean isCmp) {
    if (l.sample().isHistogram() || r.sample().isHistogram())
      return combineHistograms(e, l, r, isCmp);
    double a = l.sample().value(), b = r.sample().value();
    long ts = l.sample().ts();
    if (isCmp) {
      boolean ok = compare(a, b, e.op);
      if (e.boolModifier)
        return Optional.of(
            new SeriesSample(SeriesIds.derived(l.series()), new Sample(ts, ok ? 1f : 0f)));
      if (!ok) return Optional.empty();
      if (e.matchSpec == null) return Optional.of(l);
      SeriesId merged = mergeLabels(l.series(), r.series(), e.matchSpec);
      return Optional.of(
          new SeriesSample(new SeriesId(l.series().metric(), merged.labels(), false), l.sample()));
    }
    double v = arithmethicOp(a, b, e.op);
    return Optional.of(
        new SeriesSample(mergeLabels(l.series(), r.series(), e.matchSpec), new Sample(ts, v)));
  }

  private Optional<SeriesSample> combineHistograms(
      BinaryOpExpr e, SeriesSample l, SeriesSample r, boolean isCmp) {
    if (l.sample().isHistogram()
        && !r.sample().isHistogram()
        && (e.op.equals("</") || e.op.equals(">/"))) {
      var trimmed =
          HistogramFunctions.trim(
                  new InstantVectorResult(List.of(l)), r.sample().value(), e.op.equals(">/"))
              .data()
              .get(0);
      return Optional.of(
          new SeriesSample(mergeLabels(l.series(), r.series(), e.matchSpec), trimmed.sample()));
    }
    if (!l.sample().isHistogram() || !r.sample().isHistogram()) {
      if (isCmp) return Optional.empty();
      HistogramSeries.HistogramSample histogram;
      if (l.sample().isHistogram() && (e.op.equals("*") || e.op.equals("/"))) {
        double factor = e.op.equals("*") ? r.sample().value() : 1d / r.sample().value();
        histogram = HistogramSeries.scale(l.sample().histogram(), factor);
      } else if (r.sample().isHistogram() && e.op.equals("*")) {
        histogram = HistogramSeries.scale(r.sample().histogram(), l.sample().value());
      } else {
        return Optional.empty();
      }
      return Optional.of(
          new SeriesSample(
              mergeLabels(l.series(), r.series(), e.matchSpec),
              new Sample(l.sample().ts(), l.sample().sourceTs(), histogram)));
    }
    if (!isCmp) {
      HistogramSeries.HistogramSample histogram =
          switch (e.op) {
            case "+" -> HistogramSeries.add(l.sample().histogram(), r.sample().histogram());
            case "-" -> HistogramSeries.subtract(l.sample().histogram(), r.sample().histogram());
            default -> null;
          };
      return histogram == null
          ? Optional.empty()
          : Optional.of(
              new SeriesSample(
                  mergeLabels(l.series(), r.series(), e.matchSpec),
                  new Sample(l.sample().ts(), l.sample().sourceTs(), histogram)));
    }
    if (!e.op.equals("==") && !e.op.equals("!=")) return Optional.empty();
    boolean equal = HistogramSeries.sameValue(l.sample().histogram(), r.sample().histogram());
    boolean ok = e.op.equals("==") == equal;
    if (e.boolModifier)
      return Optional.of(
          new SeriesSample(
              SeriesIds.derived(l.series()), new Sample(l.sample().ts(), ok ? 1d : 0d)));
    return ok ? Optional.of(l) : Optional.empty();
  }

  private Double fillForMissingLeft(BinaryOpExpr e, boolean groupRight) {
    return groupRight ? e.fillSpec.right() : e.fillSpec.left();
  }

  private Double fillForMissingRight(BinaryOpExpr e, boolean groupRight) {
    return groupRight ? e.fillSpec.left() : e.fillSpec.right();
  }

  private SeriesSample filledSample(SeriesSample source, double value, MatchSpec projection) {
    var sample = source.sample();
    return new SeriesSample(
        projection == null
            ? SeriesIds.derived(source.series())
            : projectToMatchLabels(source.series(), projection),
        new Sample(sample.ts(), sample.sourceTs(), value));
  }

  private SeriesId projectToMatchLabels(SeriesId source, MatchSpec matchSpec) {
    Map<String, String> out = new HashMap<>(source.labels().tags());
    if (matchSpec.mode == MatchSpec.Mode.ON) out.keySet().retainAll(matchSpec.labels);
    else out.keySet().removeAll(matchSpec.labels);
    return SeriesIds.derived(source, out);
  }

  // ---------- Aggregate ----------

  private ExpressionResult evalAggregate(AggregateExpr e, EvalContext ctx)
      throws EvaluationException {
    if (e.args.isEmpty()) throw new EvaluationException("aggregation requires arguments");
    List<SeriesSample> out = new ArrayList<>();
    for (long t = ctx.startMs; t <= ctx.endMs; t += ctx.stepMs) {
      out.addAll(evalAggregateAt(e, ctx.withWindow(t, t)).data());
    }
    return new InstantVectorResult(out);
  }

  private InstantVectorResult evalAggregateAt(AggregateExpr e, EvalContext ctx)
      throws EvaluationException {
    String op = e.op.toLowerCase(Locale.ROOT);
    if (op.equals("count_values")) return evalCountValues(e, ctx);

    int vecIdx = 0;
    Double param = null;
    if (Set.of("topk", "bottomk", "quantile", "limitk", "limit_ratio").contains(op)) {
      if (e.args.size() != 2) throw new EvaluationException(op + ": expected two arguments");
      var pRes = TypeChecks.requireScalar(eval(e.args.get(0), ctx), op);
      param = (double) pRes.value;
      vecIdx = 1;
      validateAggregateParam(op, param);
    }

    var iv = TypeChecks.requireInstantVector(eval(e.args.get(vecIdx), ctx), op);

    Map<GroupKey, List<SeriesSample>> groups = new HashMap<>();
    for (SeriesSample s : iv.data())
      groups
          .computeIfAbsent(groupKey(e.isBy, e.groupLabels, s.series()), k -> new ArrayList<>())
          .add(s);

    List<SeriesSample> out = new ArrayList<>(groups.size());
    for (var entry : groups.entrySet()) {
      var list = entry.getValue();
      long ts = list.get(0).sample().ts();
      Map<String, String> labels = new HashMap<>(entry.getKey().labels());
      String metric = labels.remove("__name__");
      boolean dropMetricName = list.stream().anyMatch(s -> s.series().dropMetricName());
      var id = new SeriesId(metric == null ? "" : metric, new Labels(labels), dropMetricName);
      var gauges = list.stream().filter(s -> !s.sample().isHistogram()).toList();
      var histograms = list.stream().filter(s -> s.sample().isHistogram()).toList();

      switch (op) {
        case "sum" -> {
          if (gauges.isEmpty()) addHistogramAggregate(out, histograms, id, ts, false);
          else if (histograms.isEmpty()) addIfNotEmpty(out, gauges, id, ts, aggregateSum(gauges));
        }
        case "avg" -> {
          if (gauges.isEmpty()) addHistogramAggregate(out, histograms, id, ts, true);
          else if (histograms.isEmpty()) addIfNotEmpty(out, gauges, id, ts, aggregateAvg(gauges));
        }
        case "min" -> addIfNotEmpty(out, gauges, id, ts, aggregateExtrema(gauges, false));
        case "max" -> addIfNotEmpty(out, gauges, id, ts, aggregateExtrema(gauges, true));
        case "count" -> out.add(sample(id, ts, (double) list.size()));
        case "stddev" -> addIfNotEmpty(out, gauges, id, ts, stddev(gauges));
        case "stdvar" -> addIfNotEmpty(out, gauges, id, ts, stdvar(gauges));
        case "group" -> out.add(sample(id, ts, 1f));
        case "topk" -> {
          int k = clampCount(param);
          var ranked = new ArrayList<>(gauges);
          ranked.sort((a, b) -> compareRanked(a, b, true));
          out.addAll(ranked.subList(0, Math.min(k, ranked.size())));
        }
        case "bottomk" -> {
          int k = clampCount(param);
          var ranked = new ArrayList<>(gauges);
          ranked.sort((a, b) -> compareRanked(a, b, false));
          out.addAll(ranked.subList(0, Math.min(k, ranked.size())));
        }
        case "limitk" -> {
          int k = clampCount(param);
          list.sort(Comparator.comparingInt(s -> s.series().hashCode()));
          out.addAll(list.subList(0, Math.min(k, list.size())));
        }
        case "limit_ratio" -> {
          double ratio = Math.abs(param);
          boolean invert = param < 0;
          for (SeriesSample candidate : list) {
            double normalizedHash =
                Integer.toUnsignedLong(candidate.series().hashCode()) / 4294967296d;
            if ((normalizedHash < ratio) != invert) out.add(candidate);
          }
        }
        case "quantile" -> addIfNotEmpty(out, gauges, id, ts, aggQuantile(gauges, param));
        default -> throw new EvaluationException("aggregation not implemented: " + e.op);
      }
    }
    return new InstantVectorResult(out);
  }

  private InstantVectorResult evalCountValues(AggregateExpr e, EvalContext ctx) {
    if (e.args.size() != 2 || !(e.args.get(0) instanceof StringLiteralExpr labelArg))
      throw new EvaluationException(
          "count_values: expected a string label name and an instant-vector");
    if (!isValidLabelName(labelArg.value))
      throw new EvaluationException("invalid label name \"" + labelArg.value + "\"");
    var iv = TypeChecks.requireInstantVector(eval(e.args.get(1), ctx), e.op);
    Map<GroupKey, Integer> counts = new LinkedHashMap<>();
    for (SeriesSample sample : iv.data()) {
      Map<String, String> labels =
          new HashMap<>(groupKey(e.isBy, e.groupLabels, sample.series()).labels());
      labels.put(labelArg.value, formatPromValue(sample.sample()));
      counts.merge(new GroupKey(labels), 1, Integer::sum);
    }
    List<SeriesSample> out = new ArrayList<>(counts.size());
    for (var entry : counts.entrySet())
      out.add(
          sample(
              new SeriesId("", new Labels(entry.getKey().labels())),
              ctx.startMs,
              entry.getValue()));
    return new InstantVectorResult(out);
  }

  private void validateAggregateParam(String op, double param) {
    if (Double.isNaN(param) && !op.equals("quantile")) {
      String name = op.equals("limit_ratio") ? "Ratio" : "Parameter";
      throw new EvaluationException(name + " value is NaN");
    }
    if (op.equals("limit_ratio") && (param < -1f || param > 1f))
      throw new EvaluationException("ratio value must be between -1 and 1");
  }

  private int clampCount(double value) {
    if (value <= 0f) return 0;
    if (value >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
    return (int) value;
  }

  private int compareRanked(SeriesSample left, SeriesSample right, boolean descending) {
    double a = left.sample().value(), b = right.sample().value();
    if (Double.isNaN(a)) return Double.isNaN(b) ? 0 : 1;
    if (Double.isNaN(b)) return -1;
    return descending ? Double.compare(b, a) : Double.compare(a, b);
  }

  private String formatPromFloat(double value) {
    if (!Double.isFinite(value)) return Double.toString(value);
    if (value == Math.rint(value)) return Long.toString((long) value);
    return Double.toString(value);
  }

  private String formatPromValue(Sample sample) {
    return sample.isHistogram()
        ? formatPromHistogram(sample.histogram())
        : formatPromFloat(sample.value());
  }

  private String formatPromHistogram(HistogramSeries.HistogramSample histogram) {
    if (!(histogram instanceof HistogramSeries.NativeHistogramSample nativeHistogram)) {
      return "{count:"
          + formatPromDouble(histogram.count())
          + ", sum:"
          + formatPromDouble(histogram.sum())
          + "}";
    }
    List<String> buckets = new ArrayList<>();
    double base = Math.pow(2d, Math.pow(2d, -nativeHistogram.schema()));
    double[] negative = nativeHistogram.negativeBuckets();
    for (int i = negative.length - 1; i >= 0; i--) {
      int bucket = nativeHistogram.negativeOffset() + i;
      double lower = -Math.pow(base, bucket);
      double upper = -Math.pow(base, bucket - 1);
      buckets.add(
          "["
              + formatPromDouble(lower)
              + ","
              + formatPromDouble(upper)
              + "):"
              + formatPromDouble(negative[i]));
    }
    if (nativeHistogram.zeroCount() != 0) {
      buckets.add(
          "["
              + formatPromDouble(-nativeHistogram.zeroThreshold())
              + ","
              + formatPromDouble(nativeHistogram.zeroThreshold())
              + "]:"
              + formatPromDouble(nativeHistogram.zeroCount()));
    }
    double[] positive = nativeHistogram.positiveBuckets();
    for (int i = 0; i < positive.length; i++) {
      int bucket = nativeHistogram.positiveOffset() + i;
      double lower = Math.pow(base, bucket - 1);
      double upper = Math.pow(base, bucket);
      buckets.add(
          "("
              + formatPromDouble(lower)
              + ","
              + formatPromDouble(upper)
              + "]:"
              + formatPromDouble(positive[i]));
    }
    String suffix = buckets.isEmpty() ? "" : ", " + String.join(", ", buckets);
    return "{count:"
        + formatPromDouble(nativeHistogram.count())
        + ", sum:"
        + formatPromDouble(nativeHistogram.sum())
        + suffix
        + "}";
  }

  private String formatPromDouble(double value) {
    if (value == Math.rint(value)) return Long.toString((long) value);
    return Double.toString(value);
  }

  private void addIfNotEmpty(
      List<SeriesSample> out, List<SeriesSample> samples, SeriesId id, long ts, double value) {
    if (!samples.isEmpty()) out.add(sample(id, ts, value));
  }

  private void addHistogramAggregate(
      List<SeriesSample> out, List<SeriesSample> samples, SeriesId id, long ts, boolean average) {
    HistogramSeries.HistogramSample histogram = null;
    for (var sample : samples) {
      if (histogram != null
          && !HistogramSeries.compatibleRepresentation(histogram, sample.sample().histogram())) {
        return;
      }
      histogram =
          histogram == null
              ? sample.sample().histogram()
              : HistogramSeries.add(histogram, sample.sample().histogram());
    }
    if (histogram == null) return;
    if (average) histogram = HistogramSeries.scale(histogram, 1d / samples.size());
    out.add(new SeriesSample(id, new Sample(ts, ts, histogram)));
  }

  private double aggregateExtrema(List<SeriesSample> samples, boolean max) {
    double result = Double.NaN;
    for (SeriesSample sample : samples) {
      double value = sample.sample().value();
      if (Double.isNaN(value)) continue;
      if (Double.isNaN(result) || (max ? value > result : value < result)) result = value;
    }
    return result;
  }

  // ---------- Function ----------

  private ExpressionResult evalFunction(FunctionExpr e, EvalContext ctx)
      throws EvaluationException {
    validateExtendedVectorMode(e);
    return switch (e.name.toLowerCase(Locale.ROOT)) {
      // counter transforms (range-vector → instant-vector)
      case "rate" ->
          RangeFunctions.rate(
              TypeChecks.requireRangeVector(eval(e.args.get(0), ctx), e.name),
              rangeEvalContextOf(e.args.get(0), ctx),
              anchorMsOf(e.args.get(0), ctx));
      case "irate" ->
          RangeFunctions.irate(
              TypeChecks.requireRangeVector(eval(e.args.get(0), ctx), e.name),
              rangeOf(e, 0, ctx),
              ctx,
              anchorMsOf(e.args.get(0), ctx));
      case "increase" ->
          RangeFunctions.increase(
              TypeChecks.requireRangeVector(eval(e.args.get(0), ctx), e.name),
              rangeEvalContextOf(e.args.get(0), ctx),
              anchorMsOf(e.args.get(0), ctx));
      case "delta" ->
          RangeFunctions.delta(
              TypeChecks.requireRangeVector(eval(e.args.get(0), ctx), e.name),
              rangeEvalContextOf(e.args.get(0), ctx),
              anchorMsOf(e.args.get(0), ctx));
      case "idelta" ->
          RangeFunctions.idelta(
              TypeChecks.requireRangeVector(eval(e.args.get(0), ctx), e.name),
              rangeOf(e, 0, ctx),
              ctx,
              anchorMsOf(e.args.get(0), ctx));
      case "deriv" ->
          RangeFunctions.deriv(
              TypeChecks.requireRangeVector(eval(e.args.get(0), ctx), e.name),
              rangeOf(e, 0, ctx),
              ctx,
              anchorMsOf(e.args.get(0), ctx));
      // window stats (range-vector → instant-vector)
      case "avg_over_time" ->
          RangeStats.avg(
              TypeChecks.requireRangeVector(eval(e.args.get(0), ctx), e.name),
              rangeOf(e, 0, ctx),
              ctx,
              anchorMsOf(e.args.get(0), ctx));
      case "min_over_time" ->
          RangeStats.min(
              TypeChecks.requireRangeVector(eval(e.args.get(0), ctx), e.name),
              rangeOf(e, 0, ctx),
              ctx,
              anchorMsOf(e.args.get(0), ctx));
      case "max_over_time" ->
          RangeStats.max(
              TypeChecks.requireRangeVector(eval(e.args.get(0), ctx), e.name),
              rangeOf(e, 0, ctx),
              ctx,
              anchorMsOf(e.args.get(0), ctx));
      case "sum_over_time" ->
          RangeStats.sum(
              TypeChecks.requireRangeVector(eval(e.args.get(0), ctx), e.name),
              rangeOf(e, 0, ctx),
              ctx,
              anchorMsOf(e.args.get(0), ctx));
      case "count_over_time" ->
          RangeStats.count(
              TypeChecks.requireRangeVector(eval(e.args.get(0), ctx), e.name),
              rangeOf(e, 0, ctx),
              ctx,
              anchorMsOf(e.args.get(0), ctx));
      case "last_over_time" ->
          RangeStats.last(
              TypeChecks.requireRangeVector(eval(e.args.get(0), ctx), e.name),
              rangeOf(e, 0, ctx),
              ctx,
              anchorMsOf(e.args.get(0), ctx));
      case "present_over_time" ->
          RangeStats.present(
              TypeChecks.requireRangeVector(eval(e.args.get(0), ctx), e.name),
              rangeOf(e, 0, ctx),
              ctx,
              anchorMsOf(e.args.get(0), ctx));
      case "quantile_over_time" ->
          RangeStats.quantile(
              (float) TypeChecks.requireScalar(eval(e.args.get(0), ctx), e.name).value,
              TypeChecks.requireRangeVector(eval(e.args.get(1), ctx), e.name),
              rangeOf(e, 1, ctx),
              ctx,
              anchorMsOf(e.args.get(1), ctx));
      case "first_over_time" ->
          RangeStats.first(
              TypeChecks.requireRangeVector(eval(e.args.get(0), ctx), e.name),
              rangeOf(e, 0, ctx),
              ctx,
              anchorMsOf(e.args.get(0), ctx));
      case "stddev_over_time" ->
          RangeStats.stddev(
              TypeChecks.requireRangeVector(eval(e.args.get(0), ctx), e.name),
              rangeOf(e, 0, ctx),
              ctx,
              anchorMsOf(e.args.get(0), ctx));
      case "stdvar_over_time" ->
          RangeStats.stdvar(
              TypeChecks.requireRangeVector(eval(e.args.get(0), ctx), e.name),
              rangeOf(e, 0, ctx),
              ctx,
              anchorMsOf(e.args.get(0), ctx));
      case "mad_over_time" ->
          RangeStats.mad(
              TypeChecks.requireRangeVector(eval(e.args.get(0), ctx), e.name),
              rangeOf(e, 0, ctx),
              ctx,
              anchorMsOf(e.args.get(0), ctx));
      case "ts_of_min_over_time" ->
          RangeStats.timestampOf(
              TypeChecks.requireRangeVector(eval(e.args.get(0), ctx), e.name),
              rangeOf(e, 0, ctx),
              ctx,
              anchorMsOf(e.args.get(0), ctx),
              RangeStats.TimestampSelector.MIN);
      case "ts_of_max_over_time" ->
          RangeStats.timestampOf(
              TypeChecks.requireRangeVector(eval(e.args.get(0), ctx), e.name),
              rangeOf(e, 0, ctx),
              ctx,
              anchorMsOf(e.args.get(0), ctx),
              RangeStats.TimestampSelector.MAX);
      case "ts_of_first_over_time" ->
          RangeStats.timestampOf(
              TypeChecks.requireRangeVector(eval(e.args.get(0), ctx), e.name),
              rangeOf(e, 0, ctx),
              ctx,
              anchorMsOf(e.args.get(0), ctx),
              RangeStats.TimestampSelector.FIRST);
      case "ts_of_last_over_time" ->
          RangeStats.timestampOf(
              TypeChecks.requireRangeVector(eval(e.args.get(0), ctx), e.name),
              rangeOf(e, 0, ctx),
              ctx,
              anchorMsOf(e.args.get(0), ctx),
              RangeStats.TimestampSelector.LAST);
      case "changes" ->
          RangeStats.changes(
              TypeChecks.requireRangeVector(eval(e.args.get(0), ctx), e.name),
              rangeEvalContextOf(e.args.get(0), ctx),
              anchorMsOf(e.args.get(0), ctx));
      case "resets" ->
          RangeStats.resets(
              TypeChecks.requireRangeVector(eval(e.args.get(0), ctx), e.name),
              rangeEvalContextOf(e.args.get(0), ctx),
              anchorMsOf(e.args.get(0), ctx));
      case "predict_linear" ->
          RangeFunctions.predictLinear(
              TypeChecks.requireRangeVector(eval(e.args.get(0), ctx), e.name),
              rangeOf(e, 0, ctx),
              ctx,
              anchorMsOf(e.args.get(0), ctx),
              TypeChecks.requireScalar(eval(e.args.get(1), ctx), e.name).value);
      case "double_exponential_smoothing" ->
          RangeFunctions.doubleExponentialSmoothing(
              TypeChecks.requireRangeVector(eval(e.args.get(0), ctx), e.name),
              rangeOf(e, 0, ctx),
              ctx,
              anchorMsOf(e.args.get(0), ctx),
              (float) TypeChecks.requireScalar(eval(e.args.get(1), ctx), e.name).value,
              (float) TypeChecks.requireScalar(eval(e.args.get(2), ctx), e.name).value);
      // instant-vector functions
      case "abs" ->
          InstantFunctions.mapDerivedSamples(
              TypeChecks.requireInstantVector(eval(e.args.get(0), ctx), e.name), Math::abs);
      case "ceil" ->
          InstantFunctions.mapDerivedSamples(
              TypeChecks.requireInstantVector(eval(e.args.get(0), ctx), e.name), Math::ceil);
      case "floor" ->
          InstantFunctions.mapDerivedSamples(
              TypeChecks.requireInstantVector(eval(e.args.get(0), ctx), e.name), Math::floor);
      case "exp" ->
          InstantFunctions.mapDerivedSamples(
              TypeChecks.requireInstantVector(eval(e.args.get(0), ctx), e.name), FastMath::exp);
      case "ln" ->
          InstantFunctions.mapDerivedSamples(
              TypeChecks.requireInstantVector(eval(e.args.get(0), ctx), e.name), FastMath::log);
      case "sqrt" ->
          InstantFunctions.mapDerivedSamples(
              TypeChecks.requireInstantVector(eval(e.args.get(0), ctx), e.name), FastMath::sqrt);
      case "log2" ->
          InstantFunctions.mapDerivedSamples(
              TypeChecks.requireInstantVector(eval(e.args.get(0), ctx), e.name),
              v -> FastMath.log(v) / FastMath.log(2d));
      case "log10" ->
          InstantFunctions.mapDerivedSamples(
              TypeChecks.requireInstantVector(eval(e.args.get(0), ctx), e.name), FastMath::log10);
      case "sgn" ->
          InstantFunctions.mapDerivedSamples(
              TypeChecks.requireInstantVector(eval(e.args.get(0), ctx), e.name), Math::signum);
      case "round" -> evalRound(e, ctx);
      case "clamp" ->
          InstantFunctions.clamp(
              TypeChecks.requireInstantVector(eval(e.args.get(0), ctx), e.name),
              TypeChecks.requireScalar(eval(e.args.get(1), ctx), e.name).value,
              TypeChecks.requireScalar(eval(e.args.get(2), ctx), e.name).value);
      case "clamp_min" ->
          InstantFunctions.clampMin(
              TypeChecks.requireInstantVector(eval(e.args.get(0), ctx), e.name),
              TypeChecks.requireScalar(eval(e.args.get(1), ctx), e.name).value);
      case "clamp_max" ->
          InstantFunctions.clampMax(
              TypeChecks.requireInstantVector(eval(e.args.get(0), ctx), e.name),
              TypeChecks.requireScalar(eval(e.args.get(1), ctx), e.name).value);
      case "sort" ->
          InstantFunctions.sort(
              TypeChecks.requireInstantVector(eval(e.args.get(0), ctx), e.name), false);
      case "sort_desc" ->
          InstantFunctions.sort(
              TypeChecks.requireInstantVector(eval(e.args.get(0), ctx), e.name), true);
      case "sort_by_label", "sort_by_label_desc" -> {
        var vector = TypeChecks.requireInstantVector(eval(e.args.get(0), ctx), e.name);
        List<String> labels = new ArrayList<>();
        for (int i = 1; i < e.args.size(); i++)
          labels.add(((StringLiteralExpr) e.args.get(i)).value);
        yield InstantFunctions.sortByLabel(
            vector, labels, e.name.equalsIgnoreCase("sort_by_label_desc"));
      }
      case "absent" -> evalAbsent(e, ctx);
      case "absent_over_time" ->
          RangeStats.absent(
              TypeChecks.requireRangeVector(eval(e.args.get(0), ctx), e.name),
              rangeOf(e, 0, ctx),
              ctx,
              anchorMsOf(e.args.get(0), ctx),
              absentLabels(e.args.get(0)));
      case "timestamp" ->
          InstantFunctions.timestamp(
              TypeChecks.requireInstantVector(eval(e.args.get(0), ctx), e.name));
      case "sin",
          "cos",
          "tan",
          "asin",
          "acos",
          "atan",
          "sinh",
          "cosh",
          "tanh",
          "asinh",
          "acosh",
          "atanh",
          "rad",
          "deg" ->
          evalTrig(e, ctx);
      case "pi" -> {
        requireArgCount(e, 0);
        yield new ScalarResult((double) FastMath.PI);
      }
      case "info" -> evalInfo(e, ctx);
      case "histogram_count" ->
          HistogramFunctions.count(
              TypeChecks.requireInstantVector(eval(e.args.get(0), ctx), e.name));
      case "histogram_sum" ->
          HistogramFunctions.sum(TypeChecks.requireInstantVector(eval(e.args.get(0), ctx), e.name));
      case "histogram_avg" ->
          HistogramFunctions.avg(TypeChecks.requireInstantVector(eval(e.args.get(0), ctx), e.name));
      case "histogram_stddev" ->
          HistogramFunctions.stddev(
              TypeChecks.requireInstantVector(eval(e.args.get(0), ctx), e.name));
      case "histogram_stdvar" ->
          HistogramFunctions.stdvar(
              TypeChecks.requireInstantVector(eval(e.args.get(0), ctx), e.name));
      case "histogram_fraction" ->
          HistogramFunctions.fraction(
              TypeChecks.requireScalar(eval(e.args.get(0), ctx), e.name).value,
              TypeChecks.requireScalar(eval(e.args.get(1), ctx), e.name).value,
              TypeChecks.requireInstantVector(eval(e.args.get(2), ctx), e.name));
      case "histogram_quantile" ->
          HistogramFunctions.quantile(
              TypeChecks.requireScalar(eval(e.args.get(0), ctx), e.name).value,
              TypeChecks.requireInstantVector(eval(e.args.get(1), ctx), e.name));
      case "histogram_quantiles" -> {
        var vector = TypeChecks.requireInstantVector(eval(e.args.get(0), ctx), e.name);
        String label = ((StringLiteralExpr) e.args.get(1)).value;
        List<Double> quantiles = new ArrayList<>();
        for (int i = 2; i < e.args.size(); i++)
          quantiles.add(TypeChecks.requireScalar(eval(e.args.get(i), ctx), e.name).value);
        yield HistogramFunctions.quantiles(vector, label, quantiles);
      }
      // vector → scalar
      case "scalar" ->
          InstantFunctions.toScalar(
              TypeChecks.requireInstantVector(eval(e.args.get(0), ctx), e.name));
      // scalar → vector
      case "vector" -> {
        List<SeriesSample> vout = new ArrayList<>();
        for (long t = ctx.startMs; t <= ctx.endMs; t += ctx.stepMs) {
          double v =
              TypeChecks.requireScalar(eval(e.args.get(0), ctx.withWindow(t, t)), e.name).value;
          vout.add(new SeriesSample(new SeriesId("", new Labels(Map.of())), new Sample(t, v)));
        }
        yield new InstantVectorResult(vout);
      }
      // time functions
      case "time" -> new ScalarResult(ctx.endMs / 1000f);
      case "start" -> new ScalarResult(ctx.queryStartMs / 1000d);
      case "end" -> new ScalarResult(ctx.queryEndMs / 1000d);
      case "year",
          "month",
          "day_of_month",
          "day_of_week",
          "day_of_year",
          "days_in_month",
          "hour",
          "minute" ->
          InstantFunctions.calendar(
              e.name,
              e.args.isEmpty()
                  ? null
                  : TypeChecks.requireInstantVector(eval(e.args.get(0), ctx), e.name),
              ctx);
      // label manipulation
      case "label_replace" -> {
        var lriv = TypeChecks.requireInstantVector(eval(e.args.get(0), ctx), e.name);
        String dstLabel = ((StringLiteralExpr) e.args.get(1)).value;
        String replacement = ((StringLiteralExpr) e.args.get(2)).value;
        String srcLabel = ((StringLiteralExpr) e.args.get(3)).value;
        String regex = ((StringLiteralExpr) e.args.get(4)).value;
        yield labelReplace(lriv, dstLabel, replacement, srcLabel, regex);
      }
      case "label_join" -> {
        var ljiv = TypeChecks.requireInstantVector(eval(e.args.get(0), ctx), e.name);
        String dstLabel = ((StringLiteralExpr) e.args.get(1)).value;
        String separator = ((StringLiteralExpr) e.args.get(2)).value;
        List<String> srcLabels = new ArrayList<>();
        for (int i = 3; i < e.args.size(); i++)
          srcLabels.add(((StringLiteralExpr) e.args.get(i)).value);
        yield labelJoin(ljiv, dstLabel, separator, srcLabels);
      }
      default -> throw new EvaluationException("unknown function: " + e.name);
    };
  }

  // ---------- Helpers ----------

  private InstantVectorResult evalTrig(FunctionExpr e, EvalContext ctx) {
    requireArgCount(e, 1);
    return TrigFunctions.apply(
        e.name, TypeChecks.requireInstantVector(eval(e.args.get(0), ctx), e.name));
  }

  private InstantVectorResult evalRound(FunctionExpr e, EvalContext ctx) {
    if (e.args.isEmpty() || e.args.size() > 2)
      throw new EvaluationException("round: expected one or two arguments");
    double nearest =
        e.args.size() == 2 ? TypeChecks.requireScalar(eval(e.args.get(1), ctx), e.name).value : 1f;
    return InstantFunctions.mapDerivedSamples(
        TypeChecks.requireInstantVector(eval(e.args.get(0), ctx), e.name),
        value ->
            nearest == 0f
                ? value
                : !Double.isFinite(value)
                    ? value
                    : (double) (Math.floor(Math.nextUp(value / nearest) + 0.5d) * nearest));
  }

  private InstantVectorResult evalAbsent(FunctionExpr e, EvalContext ctx) {
    requireArgCount(e, 1);
    return InstantFunctions.absent(
        TypeChecks.requireInstantVector(eval(e.args.get(0), ctx), e.name),
        ctx,
        absentLabels(e.args.get(0)));
  }

  private Map<String, String> absentLabels(LogicalExpr expr) {
    SelectorExpr selector =
        switch (expr) {
          case InstantizeExpr instantize when instantize.inner instanceof SelectorExpr selected ->
              selected;
          case RangeSelectorExpr range -> range.base;
          default -> null;
        };
    if (selector == null) return Map.of();

    Map<String, String> labels = new HashMap<>();
    Set<String> ambiguous = new HashSet<>();
    for (LabelMatcher matcher : selector.matchers) {
      if ("__name__".equals(matcher.name()) || ambiguous.contains(matcher.name())) continue;
      if (matcher.op() != org.okapi.promql.parse.LabelOp.EQ
          || labels.putIfAbsent(matcher.name(), matcher.value()) != null) {
        labels.remove(matcher.name());
        ambiguous.add(matcher.name());
      }
    }
    return labels;
  }

  private void requireArgCount(FunctionExpr e, int expected) {
    if (e.args.size() != expected)
      throw new EvaluationException(
          e.name + ": expected " + expected + " arguments, got " + e.args.size());
  }

  private InstantVectorResult evalInfo(FunctionExpr e, EvalContext ctx) {
    if (e.args.isEmpty() || e.args.size() > 2)
      throw new EvaluationException("info: expected one or two arguments");

    var base = TypeChecks.requireInstantVector(eval(e.args.get(0), ctx), e.name);
    InstantizeExpr infoArg =
        e.args.size() == 1
            ? new InstantizeExpr(
                new SelectorExpr("target_info", List.of(), null, (DurationExpr) null))
            : requireInfoSelector(e.args.get(1));
    var selector = (SelectorExpr) infoArg.inner;
    boolean hasNameMatcher = selector.matchers.stream().anyMatch(m -> "__name__".equals(m.name()));
    Set<String> selectedDataLabels = new HashSet<>();
    for (var matcher : selector.matchers)
      if (!"__name__".equals(matcher.name())) selectedDataLabels.add(matcher.name());
    boolean requireInfoMatch =
        selector.matchers.stream()
            .filter(m -> !"__name__".equals(m.name()))
            .anyMatch(m -> !matchesLabel("", m));
    if (selector.metricOrNull == null && !hasNameMatcher)
      infoArg =
          new InstantizeExpr(
              new SelectorExpr("target_info", selector.matchers, selector.atTsMs, selector.offset));

    selector = (SelectorExpr) infoArg.inner;
    requireFloatInfoSeries(selector, ctx);
    var info = TypeChecks.requireInstantVector(eval(infoArg, ctx), e.name);
    Map<InfoKey, Map<String, SeriesSample>> infoByKey = new HashMap<>();
    for (var sample : info.data())
      infoByKey
          .computeIfAbsent(
              new InfoKey(identifyingLabels(sample.series()), sample.sample().ts()),
              ignored -> new LinkedHashMap<>())
          .put(sample.series().metric(), sample);

    List<SeriesSample> out = new ArrayList<>(base.data().size());
    for (var sample : base.data()) {
      if (matchesInfoMetricName(sample.series(), selector)) {
        out.add(sample);
        continue;
      }
      var infoSamples =
          infoByKey.get(new InfoKey(identifyingLabels(sample.series()), sample.sample().ts()));
      if (infoSamples == null) {
        if (!requireInfoMatch) out.add(sample);
        continue;
      }
      Map<String, String> labels = new HashMap<>(sample.series().labels().tags());
      for (var infoSample : infoSamples.values()) {
        for (var label : infoSample.series().labels().tags().entrySet()) {
          if (!selectedDataLabels.isEmpty() && !selectedDataLabels.contains(label.getKey()))
            continue;
          labels.putIfAbsent(label.getKey(), label.getValue());
        }
      }
      out.add(
          new SeriesSample(
              new SeriesId(sample.series().metric(), new Labels(labels)), sample.sample()));
    }
    return new InstantVectorResult(out);
  }

  private void requireFloatInfoSeries(SelectorExpr selector, EvalContext ctx) {
    var fetchCtx = ctx.withWindow(Math.max(0L, ctx.startMs - STALENESS_MS), ctx.endMs);
    var selected = (RangeVectorResult) evalSelector(selector, fetchCtx);
    for (var window : selected.data())
      if (!(window.scan() instanceof GaugeScan))
        throw new EvaluationException("info: info metric must contain float samples");
  }

  private boolean matchesInfoMetricName(SeriesId id, SelectorExpr selector) {
    if (selector.metricOrNull != null && !selector.metricOrNull.equals(id.metric())) return false;
    for (var matcher : selector.matchers)
      if ("__name__".equals(matcher.name()) && !matchesLabel(id.metric(), matcher)) return false;
    return true;
  }

  private boolean matchesLabel(String actual, LabelMatcher matcher) {
    return switch (matcher.op()) {
      case EQ -> actual.equals(matcher.value());
      case NE -> !actual.equals(matcher.value());
      case RE -> Pattern.compile(matcher.value()).matcher(actual).matches();
      case NRE -> !Pattern.compile(matcher.value()).matcher(actual).matches();
    };
  }

  private InstantizeExpr requireInfoSelector(LogicalExpr arg) {
    if (arg instanceof InstantizeExpr instantize && instantize.inner instanceof SelectorExpr)
      return instantize;
    throw new EvaluationException("info: second argument must be an instant selector");
  }

  private Map<String, String> identifyingLabels(SeriesId id) {
    Map<String, String> labels = new HashMap<>();
    for (String name : List.of("job", "instance")) {
      String value = id.labels().tags().get(name);
      if (value != null) labels.put(name, value);
    }
    return labels;
  }

  private record InfoKey(Map<String, String> labels, long ts) {}

  private InstantVectorResult labelReplace(
      InstantVectorResult iv, String dstLabel, String replacement, String srcLabel, String regex) {
    requireValidLabelName(dstLabel);
    Pattern p;
    try {
      p = Pattern.compile(regex);
    } catch (PatternSyntaxException e) {
      throw new EvaluationException("label_replace: invalid regex: " + e.getDescription());
    }
    List<SeriesSample> out = new ArrayList<>();
    for (var s : iv.data()) {
      Map<String, String> tags = new HashMap<>(s.series().labels().tags());
      SeriesId id = s.series();
      String srcVal = labelValue(id, srcLabel);
      Matcher m = p.matcher(srcVal);
      if (m.matches()) {
        String newVal = replacement;
        for (int i = m.groupCount(); i >= 1; i--)
          newVal = newVal.replace("$" + i, m.group(i) == null ? "" : m.group(i));
        newVal = newVal.replace("$0", m.group(0));
        if ("__name__".equals(dstLabel)) {
          id = SeriesIds.withMetric(id, newVal);
        } else if (newVal.isEmpty()) {
          tags.remove(dstLabel);
        } else {
          tags.put(dstLabel, newVal);
        }
      }
      out.add(
          new SeriesSample(
              new SeriesId(id.metric(), new Labels(tags), id.dropMetricName()), s.sample()));
    }
    return new InstantVectorResult(out);
  }

  private InstantVectorResult labelJoin(
      InstantVectorResult iv, String dstLabel, String separator, List<String> srcLabels) {
    requireValidLabelName(dstLabel);
    List<SeriesSample> out = new ArrayList<>(iv.data().size());
    for (var sample : iv.data()) {
      SeriesId sourceId = sample.series();
      SeriesId id = sourceId;
      Map<String, String> tags = new HashMap<>(id.labels().tags());
      String joined =
          srcLabels.stream()
              .map(label -> labelValue(sourceId, label))
              .collect(java.util.stream.Collectors.joining(separator));
      if ("__name__".equals(dstLabel)) {
        id = SeriesIds.withMetric(id, joined);
      } else if (joined.isEmpty()) {
        tags.remove(dstLabel);
      } else {
        tags.put(dstLabel, joined);
      }
      out.add(
          new SeriesSample(
              new SeriesId(id.metric(), new Labels(tags), id.dropMetricName()), sample.sample()));
    }
    return new InstantVectorResult(out);
  }

  private String labelValue(SeriesId id, String label) {
    return "__name__".equals(label) ? id.metric() : id.labels().tags().getOrDefault(label, "");
  }

  private void requireValidLabelName(String label) {
    if (!isValidLabelName(label))
      throw new EvaluationException("invalid label name \"" + label + "\"");
  }

  private boolean isValidLabelName(String label) {
    return "__name__".equals(label) || label.matches("[a-zA-Z_][a-zA-Z0-9_]*");
  }

  private long rangeOf(FunctionExpr e, int argIdx, EvalContext ctx) {
    return rangeOfExpr(e.args.get(argIdx), e.name, argIdx, ctx);
  }

  private long rangeOfExpr(LogicalExpr expr, String fnName, int argIdx, EvalContext ctx) {
    if (expr instanceof RangeSelectorExpr r) return r.range.evalMs(ctx);
    if (expr instanceof SubqueryExpr sq) return sq.range.evalMs(ctx);
    if (expr instanceof AtExpr at) return rangeOfExpr(at.inner, fnName, argIdx, ctx);
    if (expr instanceof OffsetExpr off) return rangeOfExpr(off.inner, fnName, argIdx, ctx);
    throw new EvaluationException(
        fnName + ": arg[" + argIdx + "] must be a range selector or subquery");
  }

  private RangeEvalContext rangeEvalContextOf(LogicalExpr expr, EvalContext ctx) {
    if (expr instanceof RangeSelectorExpr range) {
      return new RangeEvalContext(ctx, range.range.evalMs(ctx), range.mode);
    }
    if (expr instanceof SubqueryExpr subquery) {
      return new RangeEvalContext(ctx, subquery.range.evalMs(ctx), ExtendedVectorMode.NONE);
    }
    if (expr instanceof AtExpr at) return rangeEvalContextOf(at.inner, ctx);
    if (expr instanceof OffsetExpr offset) return rangeEvalContextOf(offset.inner, ctx);
    throw new EvaluationException("expected range selector");
  }

  private void validateExtendedVectorMode(FunctionExpr e) {
    if (e.args.isEmpty()) return;
    ExtendedVectorMode mode = extendedVectorModeOf(e.args.get(0));
    String function = e.name.toLowerCase(Locale.ROOT);
    if (mode == ExtendedVectorMode.SMOOTHED
        && !Set.of("delta", "increase", "rate").contains(function)) {
      throw new EvaluationException(
          "smoothed modifier can only be used with: delta, increase, rate - not with " + function);
    }
    if (mode == ExtendedVectorMode.ANCHORED
        && !Set.of("changes", "delta", "increase", "rate", "resets").contains(function)) {
      throw new EvaluationException(
          "anchored modifier can only be used with: changes, delta, increase, rate, resets - not with "
              + function);
    }
  }

  private ExtendedVectorMode extendedVectorModeOf(LogicalExpr expr) {
    if (expr instanceof RangeSelectorExpr range) return range.mode;
    if (expr instanceof AtExpr at) return extendedVectorModeOf(at.inner);
    if (expr instanceof OffsetExpr offset) return extendedVectorModeOf(offset.inner);
    return ExtendedVectorMode.NONE;
  }

  // Returns the @ anchor in ms if the expression is wrapped in AtExpr, else -1.
  private long anchorMsOf(LogicalExpr expr, EvalContext ctx) {
    if (expr instanceof AtExpr at) {
      try {
        return (long) (TypeChecks.requireScalar(eval(at.atScalar, ctx), "@").value * 1000L);
      } catch (EvaluationException ignored) {
        return -1L;
      }
    }
    return -1L;
  }

  private boolean isArithmetic(String op) {
    return switch (op) {
      case "+", "-", "*", "/", "%", "^", "atan2" -> true;
      default -> false;
    };
  }

  private boolean isComparison(String op) {
    return switch (op) {
      case "==", "!=", ">", "<", ">=", "<=" -> true;
      default -> false;
    };
  }

  private double arithmethicOp(double a, double b, String op) {
    return switch (op) {
      case "+" -> a + b;
      case "-" -> a - b;
      case "*" -> a * b;
      case "/" -> a / b; // IEEE 754: 1/0=+Inf, -1/0=-Inf, 0/0=NaN
      case "%" -> a % b;
      case "^" -> Math.pow(a, b);
      case "atan2" -> Math.atan2(a, b);
      default -> throw new EvaluationException("unknown op: " + op);
    };
  }

  private boolean compare(double a, double b, String op) {
    return switch (op) {
      case "==" -> a == b;
      case "!=" -> a != b;
      case ">" -> a > b;
      case "<" -> a < b;
      case ">=" -> a >= b;
      case "<=" -> a <= b;
      default -> throw new EvaluationException("unknown op: " + op);
    };
  }

  private InstantVectorResult mapVectorDropName(
      InstantVectorResult iv, java.util.function.Function<Double, Double> fn) {
    List<SeriesSample> out = new ArrayList<>(iv.data().size());
    for (var s : iv.data()) {
      if (s.sample().isHistogram()) continue;
      out.add(
          new SeriesSample(
              SeriesIds.derived(s.series()),
              new Sample(s.sample().ts(), s.sample().sourceTs(), fn.apply(s.sample().value()))));
    }
    return new InstantVectorResult(out);
  }

  private InstantVectorResult mapVectorArithmetic(
      InstantVectorResult vector,
      String op,
      java.util.function.Function<Double, Double> floatFn,
      double scalar,
      boolean scalarOnLeft) {
    List<SeriesSample> out = new ArrayList<>(vector.data().size());
    for (var seriesSample : vector.data()) {
      var sample = seriesSample.sample();
      if (sample.isHistogram()) {
        if (!op.equals("*") && (!op.equals("/") || scalarOnLeft)) continue;
        double factor = op.equals("*") ? scalar : 1d / scalar;
        out.add(
            new SeriesSample(
                SeriesIds.derived(seriesSample.series()),
                new Sample(
                    sample.ts(),
                    sample.sourceTs(),
                    HistogramSeries.scale(sample.histogram(), factor))));
      } else {
        out.add(
            new SeriesSample(
                SeriesIds.derived(seriesSample.series()),
                new Sample(sample.ts(), sample.sourceTs(), floatFn.apply(sample.value()))));
      }
    }
    return new InstantVectorResult(out);
  }

  private InstantVectorResult filterVector(
      InstantVectorResult iv, java.util.function.Predicate<Double> pred) {
    List<SeriesSample> out = new ArrayList<>();
    for (var s : iv.data()) {
      if (!s.sample().isHistogram() && pred.test(s.sample().value())) out.add(s);
    }
    return new InstantVectorResult(out);
  }

  private Map<JoinKey, List<SeriesSample>> indexByJoinKey(
      List<SeriesSample> samples, MatchSpec ms, Set<String> ignoredMetadata) {
    Map<JoinKey, List<SeriesSample>> map = new LinkedHashMap<>();
    for (var s : samples) {
      var key = buildJoinKey(s.series().labels().tags(), s.sample().ts(), ms, ignoredMetadata);
      if (key != null) map.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
    }
    return map;
  }

  private JoinKey buildJoinKey(
      Map<String, String> labels, long ts, MatchSpec ms, Set<String> ignoredMetadata) {
    Map<String, String> key = new TreeMap<>();
    if (ms == null) {
      for (var e : labels.entrySet())
        if (!e.getKey().equals("__name__") && !ignoredMetadata.contains(e.getKey()))
          key.put(e.getKey(), e.getValue());
      return new JoinKey(key, ts);
    }
    if (ms.mode == MatchSpec.Mode.ON) {
      for (String k : ms.labels) {
        if (ignoredMetadata.contains(k)) continue;
        key.put(k, labels.getOrDefault(k, ""));
      }
      return new JoinKey(key, ts);
    }
    for (var e : labels.entrySet()) {
      if (e.getKey().equals("__name__")) continue;
      if (!ms.labels.contains(e.getKey()) && !ignoredMetadata.contains(e.getKey()))
        key.put(e.getKey(), e.getValue());
    }
    return new JoinKey(key, ts);
  }

  private Set<String> metadataAbsentFromEitherSide(
      List<SeriesSample> left, List<SeriesSample> right) {
    Set<String> ignored = new HashSet<>();
    for (String label : List.of("__type__", "__unit__")) {
      boolean leftHasLabel =
          left.stream().anyMatch(s -> s.series().labels().tags().containsKey(label));
      boolean rightHasLabel =
          right.stream().anyMatch(s -> s.series().labels().tags().containsKey(label));
      if (!leftHasLabel || !rightHasLabel) ignored.add(label);
    }
    return ignored;
  }

  private long ceilToStep(long value, long step) {
    long remainder = Math.floorMod(value, step);
    return remainder == 0 ? value : value + step - remainder;
  }

  private long floorToStep(long value, long step) {
    return value - Math.floorMod(value, step);
  }

  private SeriesId mergeLabels(SeriesId left, SeriesId right, MatchSpec ms) {
    boolean groupRight = ms != null && ms.groupRight;
    SeriesId many = groupRight ? right : left;
    SeriesId one = groupRight ? left : right;
    Map<String, String> out = new HashMap<>(many.labels().tags());
    if (ms != null && !ms.groupLeft && !ms.groupRight) {
      if (ms.mode == MatchSpec.Mode.ON) out.keySet().retainAll(ms.labels);
      else out.keySet().removeAll(ms.labels);
    }
    if (ms != null && ms.include != null) {
      for (String k : ms.include) {
        String v = one.labels().tags().get(k);
        if (v != null) out.put(k, v);
      }
    }
    return SeriesIds.derived(many, out);
  }

  private static GroupKey groupKey(boolean isBy, List<String> groupLabels, SeriesId id) {
    Map<String, String> src = id.labels().tags();
    Map<String, String> m = new HashMap<>();
    if (groupLabels == null) {
      // No by/without modifier: aggregate all series into one group (empty key).
      return new GroupKey(m);
    }
    if (isBy) {
      for (String k : groupLabels) {
        if ("__name__".equals(k)) m.put(k, id.metric());
        else if (src.containsKey(k)) m.put(k, src.get(k));
      }
    } else {
      // without(): keep all labels except those listed (and __name__)
      for (var e : src.entrySet())
        if (!e.getKey().equals("__name__") && !groupLabels.contains(e.getKey()))
          m.put(e.getKey(), e.getValue());
    }
    return new GroupKey(m);
  }

  private static SeriesSample sample(SeriesId id, long ts, double v) {
    return new SeriesSample(id, new Sample(ts, v));
  }

  private static double aggQuantile(List<SeriesSample> list, double q) {
    if (list.isEmpty()) return Double.NaN;
    if (Double.isNaN(q)) return Double.NaN;
    if (q < 0f) return Double.NEGATIVE_INFINITY;
    if (q > 1f) return Double.POSITIVE_INFINITY;
    var arr = new ArrayList<Double>();
    for (SeriesSample sample : list) arr.add((double) sample.sample().value());
    arr.sort(
        (a, b) -> {
          if (Double.isNaN(a)) return Double.isNaN(b) ? 0 : -1;
          if (Double.isNaN(b)) return 1;
          return Double.compare(a, b);
        });
    int n = arr.size();
    if (n == 1) return arr.get(0);
    double idx = q * (n - 1);
    int i = (int) Math.floor(idx), j = (int) Math.ceil(idx);
    if (i == j) return arr.get(i);
    return (double) (arr.get(i) * (1 - (idx - i)) + arr.get(j) * (idx - i));
  }

  private static double stddev(List<SeriesSample> list) {
    return (double) Math.sqrt(stdvar(list));
  }

  private static double stdvar(List<SeriesSample> list) {
    if (list.isEmpty()) return Double.NaN;
    double mean = 0d;
    double squaredDeviationSum = 0d;
    int count = 0;
    for (SeriesSample sample : list) {
      double value = sample.sample().value();
      count++;
      double delta = value - mean;
      mean += delta / count;
      squaredDeviationSum += delta * (value - mean);
    }
    return squaredDeviationSum / count;
  }

  private static double aggregateSum(List<SeriesSample> list) {
    var sum = new CompensatedSum();
    for (SeriesSample sample : list) sum.add(sample.sample().value());
    return sum.value();
  }

  private static double aggregateAvg(List<SeriesSample> list) {
    if (list.isEmpty()) return Double.NaN;
    var sum = new CompensatedSum();
    double incrementalMean = 0d;
    boolean hasNonFiniteValue = false;
    int count = 0;
    for (SeriesSample sample : list) {
      double value = sample.sample().value();
      count++;
      sum.add(value);
      hasNonFiniteValue |= !Double.isFinite(value);
      if (!hasNonFiniteValue) {
        incrementalMean += value / count - incrementalMean / count;
      }
    }
    double total = sum.value();
    return hasNonFiniteValue || Double.isFinite(total) ? total / count : incrementalMean;
  }

  private static final class CompensatedSum {
    private double sum;
    private double compensation;

    void add(double value) {
      double next = sum + value;
      if (!Double.isFinite(sum) || !Double.isFinite(value) || !Double.isFinite(next)) {
        sum = next;
        compensation = 0d;
        return;
      }
      compensation +=
          Math.abs(sum) >= Math.abs(value) ? (sum - next) + value : (value - next) + sum;
      sum = next;
    }

    double value() {
      return sum + compensation;
    }
  }

  private record JoinKey(Map<String, String> labels, long ts) {
    @Override
    public boolean equals(Object o) {
      return o instanceof JoinKey k && Objects.equals(labels, k.labels) && ts == k.ts;
    }

    @Override
    public int hashCode() {
      return Objects.hash(labels, ts);
    }
  }

  private record GroupKey(Map<String, String> labels) {}
}

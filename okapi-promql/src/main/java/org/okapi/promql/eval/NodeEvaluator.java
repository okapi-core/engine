/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.eval;

import org.okapi.metrics.pojos.results.GaugeScan;
import org.okapi.metrics.pojos.results.Scan;
import org.okapi.metrics.pojos.results.SumScan;
import org.okapi.promql.eval.VectorData.*;
import org.okapi.promql.eval.exceptions.EvaluationException;
import org.okapi.promql.eval.nodes.*;
import org.okapi.promql.eval.ops.HistogramFunctions;
import org.okapi.promql.eval.ops.InstantFunctions;
import org.okapi.promql.eval.ops.RangeFunctions;
import org.okapi.promql.eval.ops.RangeStats;
import org.okapi.promql.eval.ops.TrigFunctions;
import org.okapi.promql.parse.LabelMatcher;
import org.apache.commons.math3.util.FastMath;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class NodeEvaluator {

  private static final long STALENESS_MS = 5 * 60_000L;

  public ExpressionResult eval(LogicalExpr expr, EvalContext ctx) throws EvaluationException {
    return switch (expr) {
      case LiteralExpr e -> new ScalarResult(e.value);
      case SelectorExpr e -> evalSelector(e, ctx);
      case InstantizeExpr e -> evalInstantize(e, ctx);
      case RangeSelectorExpr e -> evalRangeSelector(e, ctx);
      case BinaryOpExpr e -> evalBinaryOp(e, ctx);
      case AggregateExpr e -> evalAggregate(e, ctx);
      case FunctionExpr e -> evalFunction(e, ctx);
      case AtExpr e -> evalAt(e, ctx);
      case OffsetExpr e -> evalOffset(e, ctx);
      case SubqueryExpr e -> evalSubquery(e, ctx);
      case StringLiteralExpr e -> throw new EvaluationException("string literal is not directly evaluable");
    };
  }

  // ---------- Selector ----------

  private ExpressionResult evalSelector(SelectorExpr e, EvalContext ctx) {
    long start = ctx.startMs, end = ctx.endMs;
    if (e.atTsMs != null) {
      start = end = e.atTsMs;
    }
    if (e.offsetMs != null) {
      start -= e.offsetMs;
      end -= e.offsetMs;
    }
    var series = ctx.discovery.expand(e.metricOrNull, e.matchers, start, end);
    List<SeriesWindow> windows = new ArrayList<>(series.size());
    for (SeriesId id : series) {
      Scan scan = ctx.client.get(id.metric(), id.labels().tags(), ctx.resolution, start, end);
      windows.add(new SeriesWindow(id, scan));
    }
    return new RangeVectorResult(windows);
  }

  // ---------- Instantize ----------

  private ExpressionResult evalInstantize(InstantizeExpr e, EvalContext ctx)
      throws EvaluationException {
    // Expand fetch window to include the staleness lookback so the selector retrieves
    // data points that pre-date startMs but still fall within the 5-minute staleness window.
    var fetchCtx = ctx.withWindow(Math.max(0L, ctx.startMs - STALENESS_MS), ctx.endMs);
    var res = eval(e.inner, fetchCtx);
    if (!(res instanceof RangeVectorResult rv)) return res;

    List<SeriesSample> out = new ArrayList<>();
    for (SeriesWindow w : rv.data()) {
      if (!(w.scan() instanceof GaugeScan gs)) continue;
      var tsList = gs.getTimestamps();
      var valList = gs.getValues();
      int n = tsList.size();
      int idx = 0;
      for (long t = ctx.startMs; t <= ctx.endMs; t += ctx.stepMs) {
        // When the inner selector has an embedded @ or offset, the effective lookup
        // time differs from the outer step time t. Use it for the staleness window.
        long effT = effectiveStepTime(e.inner, t);
        long winStart = effT - STALENESS_MS;
        while (idx + 1 < n && tsList.get(idx + 1) <= effT) idx++;
        if (n == 0) continue;
        long ptsTs = tsList.get(idx);
        if (ptsTs <= effT && ptsTs > winStart)
          out.add(new SeriesSample(w.id(), new Sample(t, ptsTs, valList.get(idx))));
      }
    }
    return new InstantVectorResult(out);
  }

  private long effectiveStepTime(LogicalExpr inner, long outerT) {
    if (inner instanceof SelectorExpr s) {
      long base = s.atTsMs != null ? s.atTsMs : outerT;
      long off  = s.offsetMs != null ? s.offsetMs : 0L;
      return base - off;
    }
    return outerT;
  }

  // ---------- Range selector ----------

  private ExpressionResult evalRangeSelector(RangeSelectorExpr e, EvalContext ctx)
      throws EvaluationException {
    long start = ctx.startMs - e.rangeMs;
    long end = ctx.endMs;
    if (e.offsetMs != null) {
      start -= e.offsetMs;
      end -= e.offsetMs;
    }
    var base = new SelectorExpr(e.base.metricOrNull, e.base.matchers, e.base.atTsMs, null);
    var rv = (RangeVectorResult) evalSelector(base, ctx.withWindow(start, end));

    // When an offset is applied, the raw data timestamps are in shifted time. Advance them
    // by offsetMs so downstream window functions compute the correct (t - rangeMs, t] bounds
    // against the original (unshifted) evaluation times.
    if (e.offsetMs == null) return rv;
    long offset = e.offsetMs;
    List<SeriesWindow> shifted = new ArrayList<>(rv.data().size());
    for (SeriesWindow w : rv.data()) {
      if (w.scan() instanceof GaugeScan gs) {
        List<Long> ts = new ArrayList<>(gs.getTimestamps().size());
        for (Long t : gs.getTimestamps()) ts.add(t + offset);
        shifted.add(new SeriesWindow(w.id(), GaugeScan.builder()
            .universalPath(gs.getUniversalPath())
            .timestamps(Collections.unmodifiableList(ts))
            .values(gs.getValues())
            .build()));
      } else if (w.scan() instanceof SumScan ss) {
        List<Long> ts = new ArrayList<>(ss.getTs().size());
        for (Long t : ss.getTs()) ts.add(t + offset);
        shifted.add(new SeriesWindow(w.id(), SumScan.builder()
            .universalPath(ss.getUniversalPath())
            .ts(Collections.unmodifiableList(ts))
            .windowSize(ss.getWindowSize())
            .counts(ss.getCounts())
            .build()));
      } else {
        shifted.add(w);
      }
    }
    return new RangeVectorResult(shifted);
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
    return eval(e.inner, ctx.withWindow(ctx.startMs - e.offsetMs, ctx.endMs - e.offsetMs));
  }

  // ---------- Subquery ----------

  private ExpressionResult evalSubquery(SubqueryExpr e, EvalContext ctx)
      throws EvaluationException {
    long offset = e.offsetMs == null ? 0L : e.offsetMs;
    long subStart = ceilToStep(ctx.startMs - e.rangeMs - offset, e.stepMs);
    long subEnd = floorToStep(ctx.endMs - offset, e.stepMs);
    var subCtx =
        new EvalContext(
            subStart,
            subEnd,
            e.stepMs,
            ctx.nowMs,
            ctx.resolution,
            ctx.client,
            ctx.discovery,
            ctx.exec,
            ctx.statisticsMerger);
    var innerRes = eval(e.inner, subCtx);

    if (innerRes instanceof InstantVectorResult iv) {
      Map<SeriesId, List<Sample>> map = new LinkedHashMap<>();
      for (SeriesSample s : iv.data())
        map.computeIfAbsent(s.series(), k -> new ArrayList<>()).add(s.sample());

      List<SeriesWindow> out = new ArrayList<>(map.size());
      for (var entry : map.entrySet()) {
        var samples = entry.getValue();
        samples.sort(Comparator.comparingLong(Sample::ts));
        List<Long> ts = new ArrayList<>(samples.size());
        List<Float> vals = new ArrayList<>(samples.size());
        for (var smp : samples) {
          ts.add(smp.ts() + offset);
          vals.add(smp.value());
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
        } else {
          shifted.add(w);
        }
      }
      return new RangeVectorResult(shifted);
    }

    return innerRes;
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

  private ExpressionResult evalScalarScalar(BinaryOpExpr e, float a, float b) {
    if (isArithmetic(e.op)) return new ScalarResult(applyArith(a, b, e.op));
    if (isComparison(e.op)) {
      boolean ok = compare(a, b, e.op);
      return e.boolModifier ? new ScalarResult(ok ? 1f : 0f) : (ok ? new ScalarResult(a) : new ScalarResult(Float.NaN));
    }
    throw new EvaluationException("set operators require instant vectors, not scalars");
  }

  private InstantVectorResult evalScalarVector(BinaryOpExpr e, float s, InstantVectorResult v) {
    if (isArithmetic(e.op)) return mapVectorDropName(v, val -> applyArith(s, val, e.op));
    if (isComparison(e.op)) {
      return e.boolModifier
          ? mapVector(v, val -> compare(s, val, e.op) ? 1f : 0f)
          : filterVector(v, val -> compare(s, val, e.op));
    }
    throw new EvaluationException("set operators require instant vectors, not scalars");
  }

  private InstantVectorResult evalVectorScalar(BinaryOpExpr e, InstantVectorResult v, float s) {
    if (isArithmetic(e.op)) return mapVectorDropName(v, val -> applyArith(val, s, e.op));
    if (isComparison(e.op)) {
      return e.boolModifier
          ? mapVector(v, val -> compare(val, s, e.op) ? 1f : 0f)
          : filterVector(v, val -> compare(val, s, e.op));
    }
    throw new EvaluationException("set operators require instant vectors, not scalars");
  }

  private ExpressionResult evalVectorVector(
      BinaryOpExpr e, InstantVectorResult left, InstantVectorResult right)
      throws EvaluationException {
    String op = e.op.toLowerCase(Locale.ROOT);
    var ms = e.matchSpec;

    var leftIdx = indexByJoinKey(left.data(), ms);
    var rightIdx = indexByJoinKey(right.data(), ms);
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
        if (!isCmp && !isAr) throw new EvaluationException("unsupported op: " + op);

        boolean groupLeft = ms != null && ms.groupLeft;
        boolean groupRight = ms != null && ms.groupRight;
        if (groupLeft && groupRight)
          throw new EvaluationException("cannot specify both group_left and group_right");

        for (var key : leftIdx.keySet()) {
          var lList = leftIdx.get(key);
          var rList = rightIdx.get(key);
          if (rList == null || rList.isEmpty()) continue;

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
        return new InstantVectorResult(out);
      }
    }
  }

  private Optional<SeriesSample> combine(
      BinaryOpExpr e, SeriesSample l, SeriesSample r, boolean isCmp) {
    float a = l.sample().value(), b = r.sample().value();
    long ts = l.sample().ts();
    if (isCmp) {
      boolean ok = compare(a, b, e.op);
      if (e.boolModifier) return Optional.of(new SeriesSample(dropName(l.series()), new Sample(ts, ok ? 1f : 0f)));
      return ok ? Optional.of(new SeriesSample(dropName(l.series()), l.sample())) : Optional.empty();
    }
    float v = applyArith(a, b, e.op);
    return Optional.of(new SeriesSample(mergeLabels(l.series(), r.series(), e.matchSpec), new Sample(ts, v)));
  }

  // ---------- Aggregate ----------

  private ExpressionResult evalAggregate(AggregateExpr e, EvalContext ctx)
      throws EvaluationException {
    if (e.args.isEmpty()) throw new EvaluationException("aggregation requires arguments");
    String op = e.op.toLowerCase(Locale.ROOT);

    int vecIdx = 0;
    Float param = null;
    if (op.equals("topk") || op.equals("bottomk") || op.equals("quantile")) {
      var pRes = TypeChecks.requireScalar(eval(e.args.get(0), ctx), op);
      param = pRes.value;
      vecIdx = 1;
    }

    var iv = TypeChecks.requireInstantVector(eval(e.args.get(vecIdx), ctx), op);

    Map<GroupKey, List<SeriesSample>> groups = new HashMap<>();
    for (SeriesSample s : iv.data())
      groups
          .computeIfAbsent(groupKey(e.isBy, e.groupLabels, s.series().labels().tags()), k -> new ArrayList<>())
          .add(s);

    List<SeriesSample> out = new ArrayList<>(groups.size());
    for (var entry : groups.entrySet()) {
      var list = entry.getValue();
      long ts = list.get(0).sample().ts();
      var id = new SeriesId(e.op, new Labels(entry.getKey().labels()));

      switch (op) {
        case "sum" -> out.add(sample(id, ts, (float) list.stream().mapToDouble(s -> s.sample().value()).sum()));
        case "avg" -> out.add(sample(id, ts, (float) list.stream().mapToDouble(s -> s.sample().value()).average().orElse(Double.NaN)));
        case "min" -> out.add(sample(id, ts, (float) list.stream().mapToDouble(s -> s.sample().value()).min().orElse(Double.NaN)));
        case "max" -> out.add(sample(id, ts, (float) list.stream().mapToDouble(s -> s.sample().value()).max().orElse(Double.NaN)));
        case "count" -> out.add(sample(id, ts, (float) list.size()));
        case "stddev" -> out.add(sample(id, ts, stddev(list)));
        case "stdvar" -> out.add(sample(id, ts, stdvar(list)));
        case "group" -> out.add(sample(id, ts, 1f));
        case "topk" -> {
          int k = Math.max(0, Math.round(param));
          list.sort((a, b) -> Float.compare(b.sample().value(), a.sample().value()));
          list.subList(0, Math.min(k, list.size())).forEach(s ->
              out.add(new SeriesSample(new SeriesId("topk", s.series().labels()), s.sample())));
        }
        case "bottomk" -> {
          int k = Math.max(0, Math.round(param));
          list.sort(Comparator.comparingDouble(s -> s.sample().value()));
          list.subList(0, Math.min(k, list.size())).forEach(s ->
              out.add(new SeriesSample(new SeriesId("bottomk", s.series().labels()), s.sample())));
        }
        case "quantile" -> out.add(sample(id, ts, aggQuantile(list, param)));
        default -> throw new EvaluationException("aggregation not implemented: " + e.op);
      }
    }
    return new InstantVectorResult(out);
  }

  // ---------- Function ----------

  private ExpressionResult evalFunction(FunctionExpr e, EvalContext ctx)
      throws EvaluationException {
    return switch (e.name.toLowerCase(Locale.ROOT)) {
      // counter transforms (range-vector → instant-vector)
      case "rate"     -> RangeFunctions.rate    (TypeChecks.requireRangeVector(eval(e.args.get(0), ctx), e.name), rangeOf(e, 0), ctx, anchorMsOf(e.args.get(0), ctx));
      case "irate"    -> RangeFunctions.irate   (TypeChecks.requireRangeVector(eval(e.args.get(0), ctx), e.name), rangeOf(e, 0), ctx, anchorMsOf(e.args.get(0), ctx));
      case "increase" -> RangeFunctions.increase(TypeChecks.requireRangeVector(eval(e.args.get(0), ctx), e.name), rangeOf(e, 0), ctx, anchorMsOf(e.args.get(0), ctx));
      case "delta"    -> RangeFunctions.delta   (TypeChecks.requireRangeVector(eval(e.args.get(0), ctx), e.name), rangeOf(e, 0), ctx, anchorMsOf(e.args.get(0), ctx));
      case "idelta"   -> RangeFunctions.idelta  (TypeChecks.requireRangeVector(eval(e.args.get(0), ctx), e.name), rangeOf(e, 0), ctx, anchorMsOf(e.args.get(0), ctx));
      case "deriv"    -> RangeFunctions.deriv   (TypeChecks.requireRangeVector(eval(e.args.get(0), ctx), e.name), rangeOf(e, 0), ctx, anchorMsOf(e.args.get(0), ctx));
      // window stats (range-vector → instant-vector)
      case "avg_over_time"     -> RangeStats.avg    (TypeChecks.requireRangeVector(eval(e.args.get(0), ctx), e.name), rangeOf(e, 0), ctx, anchorMsOf(e.args.get(0), ctx));
      case "min_over_time"     -> RangeStats.min    (TypeChecks.requireRangeVector(eval(e.args.get(0), ctx), e.name), rangeOf(e, 0), ctx, anchorMsOf(e.args.get(0), ctx));
      case "max_over_time"     -> RangeStats.max    (TypeChecks.requireRangeVector(eval(e.args.get(0), ctx), e.name), rangeOf(e, 0), ctx, anchorMsOf(e.args.get(0), ctx));
      case "sum_over_time"     -> RangeStats.sum    (TypeChecks.requireRangeVector(eval(e.args.get(0), ctx), e.name), rangeOf(e, 0), ctx, anchorMsOf(e.args.get(0), ctx));
      case "count_over_time"   -> RangeStats.count  (TypeChecks.requireRangeVector(eval(e.args.get(0), ctx), e.name), rangeOf(e, 0), ctx, anchorMsOf(e.args.get(0), ctx));
      case "last_over_time"    -> RangeStats.last   (TypeChecks.requireRangeVector(eval(e.args.get(0), ctx), e.name), rangeOf(e, 0), ctx, anchorMsOf(e.args.get(0), ctx));
      case "present_over_time" -> RangeStats.present(TypeChecks.requireRangeVector(eval(e.args.get(0), ctx), e.name), rangeOf(e, 0), ctx, anchorMsOf(e.args.get(0), ctx));
      case "quantile_over_time" -> RangeStats.quantile(
          TypeChecks.requireScalar(eval(e.args.get(0), ctx), e.name).value,
          TypeChecks.requireRangeVector(eval(e.args.get(1), ctx), e.name),
          rangeOf(e, 1), ctx, anchorMsOf(e.args.get(1), ctx));
      case "first_over_time"   -> RangeStats.first  (TypeChecks.requireRangeVector(eval(e.args.get(0), ctx), e.name), rangeOf(e, 0), ctx, anchorMsOf(e.args.get(0), ctx));
      case "stddev_over_time"  -> RangeStats.stddev (TypeChecks.requireRangeVector(eval(e.args.get(0), ctx), e.name), rangeOf(e, 0), ctx, anchorMsOf(e.args.get(0), ctx));
      case "stdvar_over_time"  -> RangeStats.stdvar (TypeChecks.requireRangeVector(eval(e.args.get(0), ctx), e.name), rangeOf(e, 0), ctx, anchorMsOf(e.args.get(0), ctx));
      case "mad_over_time"     -> RangeStats.mad    (TypeChecks.requireRangeVector(eval(e.args.get(0), ctx), e.name), rangeOf(e, 0), ctx, anchorMsOf(e.args.get(0), ctx));
      case "changes"           -> RangeStats.changes(TypeChecks.requireRangeVector(eval(e.args.get(0), ctx), e.name), rangeOf(e, 0), ctx, anchorMsOf(e.args.get(0), ctx));
      case "resets"            -> RangeStats.resets (TypeChecks.requireRangeVector(eval(e.args.get(0), ctx), e.name), rangeOf(e, 0), ctx, anchorMsOf(e.args.get(0), ctx));
      case "predict_linear" -> RangeFunctions.predictLinear(
          TypeChecks.requireRangeVector(eval(e.args.get(0), ctx), e.name),
          rangeOf(e, 0), ctx, anchorMsOf(e.args.get(0), ctx),
          TypeChecks.requireScalar(eval(e.args.get(1), ctx), e.name).value);
      // instant-vector functions
      case "abs"   -> InstantFunctions.mapSamples(TypeChecks.requireInstantVector(eval(e.args.get(0), ctx), e.name), Math::abs);
      case "ceil"  -> InstantFunctions.mapSamples(TypeChecks.requireInstantVector(eval(e.args.get(0), ctx), e.name), v -> (float) Math.ceil(v));
      case "floor" -> InstantFunctions.mapSamples(TypeChecks.requireInstantVector(eval(e.args.get(0), ctx), e.name), v -> (float) Math.floor(v));
      case "round" -> InstantFunctions.mapSamples(TypeChecks.requireInstantVector(eval(e.args.get(0), ctx), e.name), v -> (float) Math.rint(v));
      case "clamp_min" -> InstantFunctions.clampMin(TypeChecks.requireInstantVector(eval(e.args.get(0), ctx), e.name), TypeChecks.requireScalar(eval(e.args.get(1), ctx), e.name).value);
      case "clamp_max" -> InstantFunctions.clampMax(TypeChecks.requireInstantVector(eval(e.args.get(0), ctx), e.name), TypeChecks.requireScalar(eval(e.args.get(1), ctx), e.name).value);
      case "sort"      -> InstantFunctions.sort(TypeChecks.requireInstantVector(eval(e.args.get(0), ctx), e.name), false);
      case "sort_desc" -> InstantFunctions.sort(TypeChecks.requireInstantVector(eval(e.args.get(0), ctx), e.name), true);
      case "absent"    -> InstantFunctions.absent(TypeChecks.requireInstantVector(eval(e.args.get(0), ctx), e.name), ctx);
      case "timestamp" -> InstantFunctions.timestamp(
          TypeChecks.requireInstantVector(eval(e.args.get(0), ctx), e.name));
      case "sin", "cos", "tan", "asin", "acos", "atan", "sinh", "cosh", "tanh",
          "asinh", "acosh", "atanh", "rad", "deg" -> evalTrig(e, ctx);
      case "pi" -> {
        requireArgCount(e, 0);
        yield new ScalarResult((float) FastMath.PI);
      }
      case "info" -> evalInfo(e, ctx);
      case "histogram_quantile" -> HistogramFunctions.quantile(
          TypeChecks.requireScalar(eval(e.args.get(0), ctx), e.name).value,
          TypeChecks.requireRangeVector(eval(e.args.get(1), ctx), e.name),
          rangeOf(e, 1), ctx);
      // vector → scalar
      case "scalar" -> InstantFunctions.toScalar(TypeChecks.requireInstantVector(eval(e.args.get(0), ctx), e.name));
      // scalar → vector
      case "vector" -> {
        List<SeriesSample> vout = new ArrayList<>();
        for (long t = ctx.startMs; t <= ctx.endMs; t += ctx.stepMs) {
          float v = TypeChecks.requireScalar(eval(e.args.get(0), ctx.withWindow(t, t)), e.name).value;
          vout.add(new SeriesSample(new SeriesId("", new Labels(Map.of())), new Sample(t, v)));
        }
        yield new InstantVectorResult(vout);
      }
      // time functions
      case "time" -> new ScalarResult(ctx.endMs / 1000f);
      case "year", "month", "day_of_month", "day_of_week", "day_of_year", "days_in_month",
          "hour", "minute" -> InstantFunctions.calendar(
              e.name,
              e.args.isEmpty() ? null : TypeChecks.requireInstantVector(eval(e.args.get(0), ctx), e.name),
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
      default -> throw new EvaluationException("unknown function: " + e.name);
    };
  }

  // ---------- Helpers ----------

  private InstantVectorResult evalTrig(FunctionExpr e, EvalContext ctx) {
    requireArgCount(e, 1);
    return TrigFunctions.apply(
        e.name, TypeChecks.requireInstantVector(eval(e.args.get(0), ctx), e.name));
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
            ? new InstantizeExpr(new SelectorExpr("target_info", List.of(), null, null))
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
              new SelectorExpr("target_info", selector.matchers, selector.atTsMs, selector.offsetMs));

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
          if (!selectedDataLabels.isEmpty() && !selectedDataLabels.contains(label.getKey())) continue;
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
    Pattern p = Pattern.compile(regex);
    List<SeriesSample> out = new ArrayList<>();
    for (var s : iv.data()) {
      Map<String, String> tags = new HashMap<>(s.series().labels().tags());
      String srcVal = tags.getOrDefault(srcLabel, "");
      Matcher m = p.matcher(srcVal);
      if (m.matches()) {
        String newVal = replacement;
        for (int i = m.groupCount(); i >= 1; i--)
          newVal = newVal.replace("$" + i, m.group(i) == null ? "" : m.group(i));
        newVal = newVal.replace("$0", m.group(0));
        if (newVal.isEmpty()) tags.remove(dstLabel);
        else tags.put(dstLabel, newVal);
      }
      out.add(new SeriesSample(new SeriesId(s.series().metric(), new Labels(tags)), s.sample()));
    }
    return new InstantVectorResult(out);
  }

  private String summarize(ExpressionResult result) {
    if (result instanceof InstantVectorResult iv) return "InstantVector" + iv.data();
    if (result instanceof RangeVectorResult rv) return "RangeVector(size=" + rv.data().size() + ")";
    return String.valueOf(result);
  }

  private long rangeOf(FunctionExpr e, int argIdx) {
    return rangeOfExpr(e.args.get(argIdx), e.name, argIdx);
  }

  private long rangeOfExpr(LogicalExpr expr, String fnName, int argIdx) {
    if (expr instanceof RangeSelectorExpr r) return r.rangeMs;
    if (expr instanceof SubqueryExpr sq) return sq.rangeMs;
    if (expr instanceof AtExpr at) return rangeOfExpr(at.inner, fnName, argIdx);
    if (expr instanceof OffsetExpr off) return rangeOfExpr(off.inner, fnName, argIdx);
    throw new EvaluationException(fnName + ": arg[" + argIdx + "] must be a range selector or subquery");
  }

  // Returns the @ anchor in ms if the expression is wrapped in AtExpr, else -1.
  private long anchorMsOf(LogicalExpr expr, EvalContext ctx) {
    if (expr instanceof AtExpr at) {
      try {
        return (long) (TypeChecks.requireScalar(eval(at.atScalar, ctx), "@").value * 1000L);
      } catch (EvaluationException ignored) { return -1L; }
    }
    return -1L;
  }

  private boolean isArithmetic(String op) {
    return switch (op) { case "+", "-", "*", "/", "%", "^" -> true; default -> false; };
  }

  private boolean isComparison(String op) {
    return switch (op) { case "==", "!=", ">", "<", ">=", "<=" -> true; default -> false; };
  }

  private float applyArith(float a, float b, String op) {
    return switch (op) {
      case "+" -> a + b;
      case "-" -> a - b;
      case "*" -> a * b;
      case "/" -> a / b;  // IEEE 754: 1/0=+Inf, -1/0=-Inf, 0/0=NaN
      case "%" -> a % b;
      case "^" -> (float) Math.pow(a, b);
      default -> throw new EvaluationException("unknown op: " + op);
    };
  }

  private boolean compare(float a, float b, String op) {
    return switch (op) {
      case "==" -> Float.compare(a, b) == 0;
      case "!=" -> Float.compare(a, b) != 0;
      case ">"  -> a > b;
      case "<"  -> a < b;
      case ">=" -> a >= b;
      case "<=" -> a <= b;
      default -> throw new EvaluationException("unknown op: " + op);
    };
  }

  private InstantVectorResult mapVector(InstantVectorResult iv, java.util.function.Function<Float, Float> fn) {
    List<SeriesSample> out = new ArrayList<>(iv.data().size());
    for (var s : iv.data())
      out.add(
          new SeriesSample(
              s.series(),
              new Sample(s.sample().ts(), s.sample().sourceTs(), fn.apply(s.sample().value()))));
    return new InstantVectorResult(out);
  }

  private InstantVectorResult mapVectorDropName(InstantVectorResult iv, java.util.function.Function<Float, Float> fn) {
    List<SeriesSample> out = new ArrayList<>(iv.data().size());
    for (var s : iv.data())
      out.add(
          new SeriesSample(
              dropName(s.series()),
              new Sample(s.sample().ts(), s.sample().sourceTs(), fn.apply(s.sample().value()))));
    return new InstantVectorResult(out);
  }

  private InstantVectorResult filterVector(InstantVectorResult iv, java.util.function.Predicate<Float> pred) {
    List<SeriesSample> out = new ArrayList<>();
    for (var s : iv.data())
      if (pred.test(s.sample().value())) out.add(s);
    return new InstantVectorResult(out);
  }

  private Map<JoinKey, List<SeriesSample>> indexByJoinKey(List<SeriesSample> samples, MatchSpec ms) {
    Map<JoinKey, List<SeriesSample>> map = new LinkedHashMap<>();
    for (var s : samples) {
      var key = buildJoinKey(s.series().labels().tags(), s.sample().ts(), ms);
      if (key != null) map.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
    }
    return map;
  }

  private JoinKey buildJoinKey(Map<String, String> labels, long ts, MatchSpec ms) {
    Map<String, String> key = new TreeMap<>();
    if (ms == null) {
      for (var e : labels.entrySet())
        if (!e.getKey().equals("__name__")) key.put(e.getKey(), e.getValue());
      return new JoinKey(key, ts);
    }
    if (ms.mode == MatchSpec.Mode.ON) {
      for (String k : ms.labels) {
        String v = labels.get(k);
        if (v == null) return null;
        key.put(k, v);
      }
      return new JoinKey(key, ts);
    }
    for (var e : labels.entrySet()) {
      if (e.getKey().equals("__name__")) continue;
      if (!ms.labels.contains(e.getKey())) key.put(e.getKey(), e.getValue());
    }
    return new JoinKey(key, ts);
  }

  private long ceilToStep(long value, long step) {
    long remainder = Math.floorMod(value, step);
    return remainder == 0 ? value : value + step - remainder;
  }

  private long floorToStep(long value, long step) {
    return value - Math.floorMod(value, step);
  }

  private SeriesId dropName(SeriesId id) {
    Map<String, String> tags = new HashMap<>(id.labels().tags());
    tags.remove("__name__");
    return new SeriesId("", new Labels(tags));
  }

  private SeriesId mergeLabels(SeriesId left, SeriesId right, MatchSpec ms) {
    Map<String, String> out = new HashMap<>(left.labels().tags());
    out.remove("__name__");
    if (ms != null && ms.include != null) {
      for (String k : ms.include) {
        String v = right.labels().tags().get(k);
        if (v != null) out.put(k, v);
      }
    }
    return new SeriesId("", new Labels(out));
  }

  private static GroupKey groupKey(boolean isBy, List<String> groupLabels, Map<String, String> src) {
    Map<String, String> m = new HashMap<>();
    if (groupLabels == null) {
      // No by/without modifier: aggregate all series into one group (empty key).
      return new GroupKey(m);
    }
    if (isBy) {
      for (String k : groupLabels) if (src.containsKey(k)) m.put(k, src.get(k));
    } else {
      // without(): keep all labels except those listed (and __name__)
      for (var e : src.entrySet())
        if (!e.getKey().equals("__name__") && !groupLabels.contains(e.getKey()))
          m.put(e.getKey(), e.getValue());
    }
    return new GroupKey(m);
  }

  private static SeriesSample sample(SeriesId id, long ts, float v) {
    return new SeriesSample(id, new Sample(ts, v));
  }

  private static float aggQuantile(List<SeriesSample> list, float q) {
    if (list.isEmpty()) return Float.NaN;
    var arr = list.stream().map(s -> s.sample().value()).sorted().toList();
    int n = arr.size();
    if (n == 1) return arr.get(0);
    double idx = q * (n - 1);
    int i = (int) Math.floor(idx), j = (int) Math.ceil(idx);
    if (i == j) return arr.get(i);
    return (float) (arr.get(i) * (1 - (idx - i)) + arr.get(j) * (idx - i));
  }

  private static float stddev(List<SeriesSample> list) {
    return (float) Math.sqrt(stdvar(list));
  }

  private static float stdvar(List<SeriesSample> list) {
    if (list.isEmpty()) return Float.NaN;
    double mean = list.stream().mapToDouble(s -> s.sample().value()).average().orElse(Double.NaN);
    return (float) list.stream().mapToDouble(s -> { double d = s.sample().value() - mean; return d * d; }).average().orElse(Double.NaN);
  }

  private record JoinKey(Map<String, String> labels, long ts) {
    @Override public boolean equals(Object o) {
      return o instanceof JoinKey k && Objects.equals(labels, k.labels) && ts == k.ts;
    }
    @Override public int hashCode() { return Objects.hash(labels, ts); }
  }

  private record GroupKey(Map<String, String> labels) {}
}

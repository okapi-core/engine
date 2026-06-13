/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.eval.ops;

import java.util.ArrayList;
import java.util.List;
import org.okapi.metrics.pojos.results.GaugeScan;
import org.okapi.metrics.pojos.results.Scan;
import org.okapi.metrics.pojos.results.SumScan;
import org.okapi.promql.eval.EvalContext;
import org.okapi.promql.eval.Evaluable;
import org.okapi.promql.eval.ExpressionResult;
import org.okapi.promql.eval.InstantVectorResult;
import org.okapi.promql.eval.RangeVectorResult;
import org.okapi.promql.eval.ScalarResult;
import org.okapi.promql.eval.VectorData.Sample;
import org.okapi.promql.eval.VectorData.SeriesSample;
import org.okapi.promql.eval.VectorData.SeriesWindow;
import org.okapi.promql.eval.exceptions.EvaluationException;
import org.okapi.promql.eval.nodes.AtExpr;
import org.okapi.promql.eval.nodes.FunctionExpr;
import org.okapi.promql.eval.nodes.RangeSelectorExpr;
import org.okapi.promql.eval.nodes.SubqueryExpr;
import org.okapi.promql.eval.LogicalExpr;

public final class ChangesEval implements Evaluable {
  private final FunctionExpr fn;

  public ChangesEval(FunctionExpr fn) {
    this.fn = fn;
  }

  @Override
  public ExpressionResult eval(EvalContext ctx) throws EvaluationException {
    if (fn.args.size() != 1) {
      throw new IllegalArgumentException("changes(range-vector) expects one arg");
    }
    var argRes = fn.args.get(0).lower().eval(ctx);
    if (!(argRes instanceof RangeVectorResult rv)) {
      throw new IllegalArgumentException("changes expects range vector");
    }

    long rangeMs = inferRangeFromArg(fn.args.get(0));
    List<SeriesSample> out = new ArrayList<>();
    for (SeriesWindow w : rv.data()) {
      Scan s = w.scan();
      for (long t = ctx.startMs; t <= ctx.endMs; t += ctx.stepMs) {
        long evalTimeMs = resolveEvalTimeMs(fn.args.get(0), ctx, t);
        long winStart = evalTimeMs - rangeMs;
        float value = changesInWindow(s, winStart, evalTimeMs);
        out.add(new SeriesSample(dropMetric(w.id()), new Sample(t, value)));
      }
    }
    return new InstantVectorResult(out);
  }

  private org.okapi.promql.eval.VectorData.SeriesId dropMetric(
      org.okapi.promql.eval.VectorData.SeriesId id) {
    if (id == null) return null;
    if (id.metric() == null || id.metric().isEmpty()) return id;
    return new org.okapi.promql.eval.VectorData.SeriesId("", id.labels());
  }

  private float changesInWindow(Scan scan, long start, long end) {
    if (scan instanceof GaugeScan gs) {
      return changesInWindow(gs.getTimestamps(), toFloatList(gs.getValues()), start, end);
    }
    if (scan instanceof SumScan ss) {
      return changesInWindow(ss.getTs(), toFloatList(ss.getCounts()), start, end);
    }
    return Float.NaN;
  }

  private float changesInWindow(List<Long> ts, List<Float> vals, long start, long end) {
    boolean hasPrev = false;
    float prev = 0f;
    int changes = 0;
    for (int i = 0; i < ts.size(); i++) {
      long t = ts.get(i);
      if (t <= start || t > end) continue;
      float v = vals.get(i);
      if (!hasPrev) {
        prev = v;
        hasPrev = true;
        continue;
      }
      if (Float.compare(prev, v) != 0) {
        changes++;
      }
      prev = v;
    }
    return changes;
  }

  private List<Float> toFloatList(List<? extends Number> nums) {
    List<Float> out = new ArrayList<>(nums.size());
    for (Number n : nums) {
      out.add(n.floatValue());
    }
    return out;
  }

  private long inferRangeFromArg(LogicalExpr arg) {
    if (arg instanceof RangeSelectorExpr r) return r.rangeMs;
    if (arg instanceof SubqueryExpr sq) return sq.rangeMs;
    if (arg instanceof AtExpr at) return inferRangeFromArg(at.inner);
    throw new IllegalStateException("changes requires range selector or subquery");
  }

  private long resolveEvalTimeMs(LogicalExpr arg, EvalContext ctx, long fallback)
      throws EvaluationException {
    if (arg instanceof AtExpr at) {
      var res = at.atScalar.lower().eval(ctx);
      if (!(res instanceof ScalarResult s)) {
        throw new IllegalArgumentException("@ modifier requires scalar RHS (unix seconds)");
      }
      return (long) (s.value * 1000L);
    }
    return fallback;
  }
}

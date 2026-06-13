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

public final class PredictLinearEval implements Evaluable {
  private final FunctionExpr fn;

  public PredictLinearEval(FunctionExpr fn) {
    this.fn = fn;
  }

  @Override
  public ExpressionResult eval(EvalContext ctx) throws EvaluationException {
    if (fn.args.size() != 2) {
      throw new IllegalArgumentException("predict_linear(range-vector, t) expects two args");
    }
    var argRes = fn.args.get(0).lower().eval(ctx);
    if (!(argRes instanceof org.okapi.promql.eval.RangeVectorResult rv)) {
      throw new IllegalArgumentException("predict_linear expects range vector");
    }
    var tRes = fn.args.get(1).lower().eval(ctx);
    if (!(tRes instanceof ScalarResult tScalar)) {
      throw new IllegalArgumentException("predict_linear expects scalar duration");
    }
    float durationSeconds = tScalar.value;

    long rangeMs = inferRangeFromArg(fn.args.get(0));
    List<SeriesSample> out = new ArrayList<>();
    for (SeriesWindow w : rv.data()) {
      Scan s = w.scan();
      for (long t = ctx.startMs; t <= ctx.endMs; t += ctx.stepMs) {
        long evalTimeMs = resolveEvalTimeMs(fn.args.get(0), ctx, t);
        long winStart = evalTimeMs - rangeMs;
        float value = predictAt(s, winStart, evalTimeMs, durationSeconds);
        out.add(new SeriesSample(dropMetric(w.id()), new Sample(t, value)));
      }
    }
    return new InstantVectorResult(out);
  }

  private float predictAt(Scan scan, long startMs, long endMs, float durationSeconds) {
    if (scan instanceof GaugeScan gs) {
      return predictFromSeries(gs.getTimestamps(), toFloatList(gs.getValues()), startMs, endMs, durationSeconds);
    }
    if (scan instanceof SumScan ss) {
      return predictFromSeries(ss.getTs(), toFloatList(ss.getCounts()), startMs, endMs, durationSeconds);
    }
    return Float.NaN;
  }

  private float predictFromSeries(
      List<Long> ts, List<Float> vals, long startMs, long endMs, float durationSeconds) {
    int n = 0;
    double sumX = 0d;
    double sumY = 0d;
    double sumXX = 0d;
    double sumXY = 0d;

    for (int i = 0; i < ts.size(); i++) {
      long t = ts.get(i);
      if (t <= startMs || t > endMs) {
        continue;
      }
      float v = vals.get(i);
      if (!Float.isFinite(v)) {
        return Float.NaN;
      }
      double x = t / 1000.0d;
      double y = v;
      n++;
      sumX += x;
      sumY += y;
      sumXX += x * x;
      sumXY += x * y;
    }
    if (n < 2) {
      return Float.NaN;
    }
    double denom = n * sumXX - sumX * sumX;
    if (denom == 0d) {
      return Float.NaN;
    }
    double slope = (n * sumXY - sumX * sumY) / denom;
    double intercept = (sumY - slope * sumX) / n;
    double atSeconds = endMs / 1000.0d + durationSeconds;
    return (float) (slope * atSeconds + intercept);
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
    throw new IllegalStateException("predict_linear requires range selector or subquery");
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

  private org.okapi.promql.eval.VectorData.SeriesId dropMetric(
      org.okapi.promql.eval.VectorData.SeriesId id) {
    if (id == null) return null;
    if (id.metric() == null || id.metric().isEmpty()) return id;
    return new org.okapi.promql.eval.VectorData.SeriesId("", id.labels());
  }
}

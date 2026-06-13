/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.eval.ops;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.okapi.promql.eval.EvalContext;
import org.okapi.promql.eval.Evaluable;
import org.okapi.promql.eval.ExpressionResult;
import org.okapi.promql.eval.InstantVectorResult;
import org.okapi.promql.eval.ScalarResult;
import org.okapi.promql.eval.VectorData.Labels;
import org.okapi.promql.eval.VectorData.Sample;
import org.okapi.promql.eval.VectorData.SeriesId;
import org.okapi.promql.eval.VectorData.SeriesSample;
import org.okapi.promql.eval.exceptions.EvaluationException;
import org.okapi.promql.eval.nodes.FunctionExpr;

public final class MinuteFuncEval implements Evaluable {
  private final FunctionExpr fn;

  public MinuteFuncEval(FunctionExpr fn) {
    this.fn = fn;
  }

  @Override
  public ExpressionResult eval(EvalContext ctx) throws EvaluationException {
    if (fn.args.isEmpty()) {
      SeriesId id = new SeriesId("", new Labels(Map.of()));
      List<SeriesSample> out = new ArrayList<>();
      for (long t = ctx.startMs; t <= ctx.endMs; t += ctx.stepMs) {
        float minutes = minuteOfHour(t / 1000.0f);
        out.add(new SeriesSample(id, new Sample(t, minutes)));
      }
      return new InstantVectorResult(out);
    }
    if (fn.args.size() != 1) {
      throw new IllegalArgumentException("minute(v) expects 0 or 1 arg");
    }
    ExpressionResult res = fn.args.get(0).lower().eval(ctx);
    if (res instanceof InstantVectorResult iv) {
      List<SeriesSample> out = new ArrayList<>(iv.data().size());
      for (SeriesSample s : iv.data()) {
        float minutes = minuteOfHour(s.sample().value());
        out.add(new SeriesSample(dropMetric(s.series()), new Sample(s.sample().ts(), minutes)));
      }
      return new InstantVectorResult(out);
    }
    if (res instanceof ScalarResult s) {
      return new ScalarResult(minuteOfHour(s.value));
    }
    throw new IllegalArgumentException("minute expects instant vector or scalar");
  }

  private float minuteOfHour(float seconds) {
    long totalSeconds = (long) Math.floor(seconds);
    long minutes = (totalSeconds / 60L) % 60L;
    if (minutes < 0) {
      minutes += 60;
    }
    return (float) minutes;
  }

  private SeriesId dropMetric(SeriesId id) {
    if (id == null) return null;
    if (id.metric() == null || id.metric().isEmpty()) return id;
    return new SeriesId("", id.labels());
  }
}

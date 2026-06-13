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
import org.okapi.promql.eval.nodes.FunctionExpr;

public final class PiFuncEval implements Evaluable {
  private final FunctionExpr fn;

  PiFuncEval(FunctionExpr fn) {
    this.fn = fn;
  }

  @Override
  public ExpressionResult eval(EvalContext ctx) {
    if (!fn.args.isEmpty()) {
      throw new IllegalArgumentException("pi() expects no args");
    }
    float value = (float) Math.PI;
    if (ctx.startMs == ctx.endMs) {
      return new ScalarResult(value);
    }
    SeriesId id = new SeriesId("", new Labels(Map.of()));
    List<SeriesSample> out = new ArrayList<>();
    for (long t = ctx.startMs; t <= ctx.endMs; t += ctx.stepMs) {
      out.add(new SeriesSample(id, new Sample(t, value)));
    }
    return new InstantVectorResult(out);
  }
}

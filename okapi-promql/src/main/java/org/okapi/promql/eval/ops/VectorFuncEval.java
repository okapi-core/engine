/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.eval.ops;

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

public final class VectorFuncEval implements Evaluable {
  private final FunctionExpr fn;

  public VectorFuncEval(FunctionExpr fn) {
    this.fn = fn;
  }

  @Override
  public ExpressionResult eval(EvalContext ctx) throws EvaluationException {
    if (fn.args.size() != 1) {
      throw new IllegalArgumentException("vector(scalar) expects one arg");
    }
    ExpressionResult res = fn.args.get(0).lower().eval(ctx);
    if (res instanceof InstantVectorResult iv) {
      return iv;
    }
    if (!(res instanceof ScalarResult s)) {
      throw new IllegalArgumentException("vector expects scalar");
    }
    SeriesId id = new SeriesId("", new Labels(Map.of()));
    SeriesSample sample = new SeriesSample(id, new Sample(ctx.endMs, s.getValue()));
    return new InstantVectorResult(List.of(sample));
  }
}

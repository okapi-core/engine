/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.eval.ops;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.okapi.promql.eval.EvalContext;
import org.okapi.promql.eval.Evaluable;
import org.okapi.promql.eval.ExpressionResult;
import org.okapi.promql.eval.InstantVectorResult;
import org.okapi.promql.eval.RangeVectorResult;
import org.okapi.promql.eval.VectorData.Labels;
import org.okapi.promql.eval.VectorData.SeriesId;
import org.okapi.promql.eval.VectorData.SeriesSample;
import org.okapi.promql.eval.VectorData.SeriesWindow;
import org.okapi.promql.eval.exceptions.EvaluationException;
import org.okapi.promql.eval.nodes.FunctionExpr;
import org.okapi.promql.eval.nodes.StringLiteralExpr;

public final class LabelReplaceEval implements Evaluable {
  private final FunctionExpr fn;

  public LabelReplaceEval(FunctionExpr fn) {
    this.fn = fn;
  }

  @Override
  public ExpressionResult eval(EvalContext ctx) throws EvaluationException {
    if (fn.args.size() != 5) {
      throw new IllegalArgumentException("label_replace(v, dst, replacement, src, regex)");
    }

    String dst = requireStringArg(1);
    String replacement = requireStringArg(2);
    String src = requireStringArg(3);
    String regex = requireStringArg(4);
    Pattern pattern = Pattern.compile(regex);

    ExpressionResult res = fn.args.get(0).lower().eval(ctx);
    if (res instanceof InstantVectorResult iv) {
      List<SeriesSample> out = new ArrayList<>(iv.data().size());
      for (SeriesSample s : iv.data()) {
        SeriesId id = replaceLabel(s.series(), src, dst, replacement, pattern);
        out.add(new SeriesSample(id, s.sample()));
      }
      return new InstantVectorResult(out);
    }
    if (res instanceof RangeVectorResult rv) {
      List<SeriesWindow> out = new ArrayList<>(rv.data().size());
      for (SeriesWindow w : rv.data()) {
        SeriesId id = replaceLabel(w.id(), src, dst, replacement, pattern);
        out.add(new SeriesWindow(id, w.scan()));
      }
      return new RangeVectorResult(out);
    }
    throw new IllegalArgumentException("label_replace expects instant or range vector");
  }

  private String requireStringArg(int idx) {
    var expr = fn.args.get(idx);
    if (expr instanceof StringLiteralExpr s) {
      return s.value;
    }
    throw new IllegalArgumentException("label_replace expects string literal args");
  }

  private SeriesId replaceLabel(
      SeriesId id, String src, String dst, String replacement, Pattern pattern) {
    String srcValue = "__name__".equals(src) ? id.metric() : id.labels().tags().getOrDefault(src, "");
    var matcher = pattern.matcher(srcValue);
    if (!matcher.find()) {
      return id;
    }
    String replaced = matcher.replaceAll(replacement);

    Map<String, String> labels = new HashMap<>(id.labels().tags());
    String metric = id.metric();
    if ("__name__".equals(dst)) {
      metric = replaced;
    } else if (replaced.isEmpty()) {
      labels.remove(dst);
    } else {
      labels.put(dst, replaced);
    }
    return new SeriesId(metric, new Labels(labels));
  }
}

/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.eval;

import java.util.List;
import java.util.concurrent.*;
import org.okapi.promql.eval.exceptions.EvaluationException;
import org.okapi.promql.eval.labelmatch.LabelMatchVisitor;
import org.okapi.promql.eval.labelmatch.MetricMatchCondition;
import org.okapi.promql.eval.ts.RESOLUTION;
import org.okapi.promql.eval.ts.SeriesDiscovery;
import org.okapi.promql.eval.ts.StatisticsMerger;
import org.okapi.promql.eval.ts.TsClient;
import org.okapi.promql.eval.visitor.ExpressionVisitor;
import org.okapi.promql.parser.PromQLParser;

public final class ExpressionEvaluator {
  private static final long DEFAULT_INSTANT_STEP_MS = 1_000L;

  private final TsClient client;
  private final SeriesDiscovery discovery;
  private final ExecutorService exec;
  private final StatisticsMerger statisticsMerger;
  private final MetricTypeResolver metricTypeResolver;

  public ExpressionEvaluator(
      TsClient client,
      SeriesDiscovery discovery,
      ExecutorService exec,
      StatisticsMerger statisticsMerger) {
    this(client, discovery, exec, statisticsMerger, null);
  }

  public ExpressionEvaluator(
      TsClient client,
      SeriesDiscovery discovery,
      ExecutorService exec,
      StatisticsMerger statisticsMerger,
      MetricTypeResolver metricTypeResolver) {
    this.client = client;
    this.discovery = discovery;
    this.exec = exec;
    this.statisticsMerger = statisticsMerger;
    this.metricTypeResolver = metricTypeResolver;
  }

  public ExpressionResult evaluate(
      String promql, long startMs, long endMs, long stepMs, PromQLParser parser)
      throws EvaluationException {
    long nowMs = System.currentTimeMillis();
    var logical = new ExpressionVisitor().visit(parser.expression());
    var ctx =
        new EvalContext(
            startMs,
            endMs,
            stepMs,
            chooseResolution(stepMs),
            client,
            discovery,
            exec,
            metricTypeResolver);
    return logical.lower().eval(ctx);
  }

  public ExpressionResult evaluateAt(String promql, long tsMs, PromQLParser parser)
      throws EvaluationException {
    long nowMs = System.currentTimeMillis();
    var logical = new ExpressionVisitor().visit(parser.expression());
    var ctx =
        new EvalContext(
            tsMs,
            tsMs,
            effStepMs,
            chooseResolution(effStepMs),
            client,
            discovery,
            exec,
            metricTypeResolver);

    return logical.lower().eval(ctx);
  }

  public List<VectorData.SeriesId> find(PromQLParser parser, long start, long end) {
    var tree = parser.expression();
    var labelMatcher = new LabelMatchVisitor();
    var conditions = (MetricMatchCondition) labelMatcher.visit(tree);
    return discovery.expand(conditions.getMetricNameOrNull(), conditions.getLabelMatchers(), start, end);
  }

  private static RESOLUTION chooseResolution(long stepMs) {
    if (stepMs <= 1_000L) return RESOLUTION.SECONDLY;
    if (stepMs <= 60_000L) return RESOLUTION.MINUTELY;
    return RESOLUTION.HOURLY;
  }
}

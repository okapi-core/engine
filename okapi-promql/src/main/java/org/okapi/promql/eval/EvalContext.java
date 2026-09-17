/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.eval;

import java.util.concurrent.*;
import org.okapi.promql.eval.ts.RESOLUTION;
import org.okapi.promql.eval.ts.SeriesDiscovery;
import org.okapi.promql.eval.ts.StatisticsMerger;
import org.okapi.promql.eval.ts.TsClient;

public final class EvalContext {
  public final long startMs, endMs, stepMs;
  public final long queryStartMs, queryEndMs;

  /** Wall-clock time captured once at evaluation entry. Used by time() and staleness checks. */
  public final long nowMs;

  public final RESOLUTION resolution;
  public final TsClient client;
  public final SeriesDiscovery discovery;
  public final ExecutorService exec;
  public final StatisticsMerger statisticsMerger;

  public EvalContext(
      long startMs,
      long endMs,
      long stepMs,
      long nowMs,
      RESOLUTION res,
      TsClient client,
      SeriesDiscovery discovery,
      ExecutorService exec,
      StatisticsMerger statisticsMerger) {
    this(
        startMs,
        endMs,
        stepMs,
        startMs,
        endMs,
        nowMs,
        res,
        client,
        discovery,
        exec,
        statisticsMerger);
  }

  private EvalContext(
      long startMs,
      long endMs,
      long stepMs,
      long queryStartMs,
      long queryEndMs,
      long nowMs,
      RESOLUTION res,
      TsClient client,
      SeriesDiscovery discovery,
      ExecutorService exec,
      StatisticsMerger statisticsMerger) {
    this.startMs = startMs;
    this.endMs = endMs;
    this.stepMs = stepMs;
    this.queryStartMs = queryStartMs;
    this.queryEndMs = queryEndMs;
    this.nowMs = nowMs;
    this.resolution = res;
    this.client = client;
    this.discovery = discovery;
    this.exec = exec;
    this.statisticsMerger = statisticsMerger;
  }

  public EvalContext withWindow(long newStartMs, long newEndMs) {
    return new EvalContext(
        newStartMs,
        newEndMs,
        stepMs,
        queryStartMs,
        queryEndMs,
        nowMs,
        resolution,
        client,
        discovery,
        exec,
        statisticsMerger);
  }

  public EvalContext withWindow(long newStartMs, long newEndMs, long newStepMs) {
    return new EvalContext(
        newStartMs,
        newEndMs,
        newStepMs,
        queryStartMs,
        queryEndMs,
        nowMs,
        resolution,
        client,
        discovery,
        exec,
        statisticsMerger);
  }
}

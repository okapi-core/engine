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
  /** Wall-clock time captured once at evaluation entry. Used by time() and staleness checks. */
  public final long nowMs;

  public final RESOLUTION resolution;
  public final TsClient client;
  public final SeriesDiscovery discovery;
  public final ExecutorService exec;
  public final MetricTypeResolver metricTypeResolver;
  public final long nowMs;

  public EvalContext(
      long startMs,
      long endMs,
      long stepMs,
      long nowMs,
      RESOLUTION res,
      TsClient client,
      SeriesDiscovery discovery,
      ExecutorService exec) {
    this(startMs, endMs, stepMs, res, client, discovery, exec, null);
  }

  public EvalContext(
      long startMs,
      long endMs,
      long stepMs,
      RESOLUTION res,
      TsClient client,
      SeriesDiscovery discovery,
      ExecutorService exec,
      MetricTypeResolver metricTypeResolver) {
    this.startMs = startMs;
    this.endMs = endMs;
    this.stepMs = stepMs;
    this.nowMs = nowMs;
    this.resolution = res;
    this.client = client;
    this.discovery = discovery;
    this.exec = exec;
    this.metricTypeResolver = metricTypeResolver;
  }
}

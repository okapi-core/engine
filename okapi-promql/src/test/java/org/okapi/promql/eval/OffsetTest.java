/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.okapi.promql.extractor.TimeSeriesExtractor.findValue;

import java.util.concurrent.Executors;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.jupiter.api.Test;
import org.okapi.promql.MockStatsMerger;
import org.okapi.promql.TestFixtures;
import org.okapi.promql.eval.VectorData.*;
import org.okapi.promql.eval.exceptions.EvaluationException;
import org.okapi.promql.parser.PromQLLexer;
import org.okapi.promql.parser.PromQLParser;

/**
 * The offset modifier shifts the evaluation window backward in time.
 * `avg_over_time(cpu_usage[2m])` at t2 uses window (t0,t2] → {t1=20, t2=30} → avg=25.
 * `avg_over_time(cpu_usage[2m] offset 1m)` at t2 shifts to look at (t0-1m, t1] → {t0=10, t1=20} → avg=15.
 */
public class OffsetTest {

  private static ExpressionResult eval(
      ExpressionEvaluator ev, String q, long start, long end, long step) {
    var parser = new PromQLParser(new CommonTokenStream(new PromQLLexer(CharStreams.fromString(q))));
    return ev.evaluate(q, start, end, step, parser);
  }

  @Test
  void rangeVector_offset_shiftsWindowBackInTime() throws EvaluationException {
    var cm = TestFixtures.buildCommonMocks();
    var ev = new ExpressionEvaluator(cm.client, cm.discovery, Executors.newFixedThreadPool(2), new MockStatsMerger());

    // Without offset at t2: window (t0,t2] → {t1=20, t2=30} → avg=25
    var noOffset = (InstantVectorResult) eval(ev, "avg_over_time(cpu_usage[2m])", cm.t2, cm.t2, cm.step);
    assertEquals(25f, findValue(noOffset, cm.cpuUsageApiI1, cm.t2), 1e-4);

    // With offset 1m at t2: window (t2-1m-2m, t2-1m] = (t2-3m, t1] = (t0-1m, t1]
    // Data in (t0-1m, t1]: t0=10 and t1=20 → avg=15
    var withOffset = (InstantVectorResult) eval(ev, "avg_over_time(cpu_usage[2m] offset 1m)", cm.t2, cm.t2, cm.step);
    assertEquals(15f, findValue(withOffset, cm.cpuUsageApiI1, cm.t2), 1e-4);
  }

  @Test
  void rangeVector_offset_multiStep_allPointsShifted() throws EvaluationException {
    var cm = TestFixtures.buildCommonMocks();
    var ev = new ExpressionEvaluator(cm.client, cm.discovery, Executors.newFixedThreadPool(2), new MockStatsMerger());

    // offset 1m for multi-step t2..t3:
    // At t2 with offset: window (t0-1m, t1] → avg(10,20)=15
    // At t3 with offset: window (t1-1m, t2] = (t0, t2] → avg(20,30)=25
    var iv = (InstantVectorResult) eval(ev, "avg_over_time(cpu_usage[2m] offset 1m)", cm.t2, cm.t3, cm.step);
    assertEquals(15f, findValue(iv, cm.cpuUsageApiI1, cm.t2), 1e-4);
    assertEquals(25f, findValue(iv, cm.cpuUsageApiI1, cm.t3), 1e-4);
  }
}

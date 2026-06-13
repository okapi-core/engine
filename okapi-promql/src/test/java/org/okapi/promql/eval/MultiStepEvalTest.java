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
 * Multi-step evaluation: queries evaluated from start to end with a step interval
 * must produce one sample per series per step.
 */
public class MultiStepEvalTest {

  private static ExpressionResult eval(
      ExpressionEvaluator ev, String q, long start, long end, long step) {
    var parser = new PromQLParser(new CommonTokenStream(new PromQLLexer(CharStreams.fromString(q))));
    return ev.evaluate(q, start, end, step, parser);
  }

  @Test
  void multiStep_avg_over_time_correctValuesAtEachStep() throws EvaluationException {
    var cm = TestFixtures.buildCommonMocks();
    var ev = new ExpressionEvaluator(cm.client, cm.discovery, Executors.newFixedThreadPool(2), new MockStatsMerger());

    // Evaluate t1..t3 (3 steps). 2-minute window per step.
    var iv = (InstantVectorResult) eval(ev, "avg_over_time(cpu_usage[2m])", cm.t1, cm.t3, cm.step);

    // 2 series × 3 steps = 6 samples total
    assertEquals(6, iv.data().size());

    var i1 = new SeriesId("", new Labels(cm.cpuUsageApiI1Tags));
    var i2 = new SeriesId("", new Labels(cm.cpuUsageApiI2Tags));
    // cpu_i1: t1→avg(t0=10,t1=20)=15; t2→avg(t1=20,t2=30)=25; t3→avg(t2=30,t3=40)=35
    assertEquals(15f, findValue(iv, i1, cm.t1), 1e-4);
    assertEquals(25f, findValue(iv, i1, cm.t2), 1e-4);
    assertEquals(35f, findValue(iv, i1, cm.t3), 1e-4);

    // cpu_i2: t1→avg(t0=40,t1=60)=50; t2→avg(t1=60,t2=80)=70; t3→avg(t2=80,t3=100)=90
    assertEquals(50f, findValue(iv, i2, cm.t1), 1e-4);
    assertEquals(70f, findValue(iv, i2, cm.t2), 1e-4);
    assertEquals(90f, findValue(iv, i2, cm.t3), 1e-4);
  }

  @Test
  void multiStep_rate_correctValuesAtEachStep() throws EvaluationException {
    var cm = TestFixtures.buildCommonMocks();
    var ev = new ExpressionEvaluator(cm.client, cm.discovery, Executors.newFixedThreadPool(2), new MockStatsMerger());

    // Evaluate t1..t3. rate windows (t-2m, t].
    // t1: {t0=60, t1=120} sum=180 / 120s = 1.5/s
    // t2: {t1=120, t2=180} sum=300 / 120s = 2.5/s
    // t3: {t2=180, t3=240} sum=420 / 120s = 3.5/s
    var iv = (InstantVectorResult) eval(ev, "rate(http_requests_counter[2m])", cm.t1, cm.t3, cm.step);

    var http = new SeriesId("", new Labels(cm.httpRequestsCounterApiTags));
    assertEquals(1.5f, findValue(iv, http, cm.t1), 1e-4);
    assertEquals(2.5f, findValue(iv, http, cm.t2), 1e-4);
    assertEquals(3.5f, findValue(iv, http, cm.t3), 1e-4);
  }

  @Test
  void multiStep_binaryOp_producesCorrectSeriesCountPerStep() throws EvaluationException {
    var cm = TestFixtures.buildCommonMocks();
    var ev = new ExpressionEvaluator(cm.client, cm.discovery, Executors.newFixedThreadPool(2), new MockStatsMerger());

    // cpu_usage and mem_usage share i1 key; unless removes it.
    // cpu has i1 and i2; unless mem(i1) → only i2 remains, at each step.
    var iv = (InstantVectorResult) eval(ev,
        "avg_over_time(cpu_usage[2m]) unless on(job,instance) avg_over_time(mem_usage[2m])",
        cm.t1, cm.t3, cm.step);

    // 1 series (i2) × 3 steps = 3 samples
    assertEquals(3, iv.data().size());
    var i2u = new SeriesId("", new Labels(cm.cpuUsageApiI2Tags));
    assertEquals(50f, findValue(iv, i2u, cm.t1), 1e-4);
    assertEquals(70f, findValue(iv, i2u, cm.t2), 1e-4);
    assertEquals(90f, findValue(iv, i2u, cm.t3), 1e-4);
  }
}

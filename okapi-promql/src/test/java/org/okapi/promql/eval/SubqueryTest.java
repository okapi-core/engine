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
 * Subquery syntax [range:step] evaluates an inner instant-vector-producing expression
 * over a range and converts the result to a range vector for further aggregation.
 */
public class SubqueryTest {

  private static ExpressionResult eval(
      ExpressionEvaluator ev, String q, long start, long end, long step) {
    var parser = new PromQLParser(new CommonTokenStream(new PromQLLexer(CharStreams.fromString(q))));
    return ev.evaluate(q, start, end, step, parser);
  }

  @Test
  void subquery_withFixedStep_producesCorrectAgg() throws EvaluationException {
    var cm = TestFixtures.buildCommonMocks();
    var ev = new ExpressionEvaluator(cm.client, cm.discovery, Executors.newFixedThreadPool(2), new MockStatsMerger());

    // avg_over_time(cpu_usage[2m:1m]) at t2:
    // Subquery evaluates cpu_usage instant vector at t0,t1,t2 (2m range, 1m step).
    // Subquery result as range data: cpu_i1=[t0=10,t1=20,t2=30]
    // avg_over_time over range (t0,t2] (strict start): excludes t0 (t0 > t0 false), keeps t1=20,t2=30
    // avg = 25
    var iv = (InstantVectorResult) eval(ev, "avg_over_time(cpu_usage[2m:1m])", cm.t2, cm.t2, cm.step);
    assertEquals(ValueType.INSTANT_VECTOR, iv.type());
    assertEquals(25f, findValue(iv, cm.cpuUsageApiI1, cm.t2), 1e-4);
    assertEquals(70f, findValue(iv, cm.cpuUsageApiI2, cm.t2), 1e-4);
  }

  @Test
  void subquery_max_over_time_aggregatesSubEvals() throws EvaluationException {
    var cm = TestFixtures.buildCommonMocks();
    var ev = new ExpressionEvaluator(cm.client, cm.discovery, Executors.newFixedThreadPool(2), new MockStatsMerger());

    // max_over_time(avg_over_time(cpu_usage[1m])[2m:1m]) at t2:
    // Inner: avg_over_time(cpu_usage[1m]) evaluated at t0,t1,t2
    //   At t0: window (t0-1m, t0]: only t0=10. avg=10.
    //   At t1: window (t0, t1]: only t1=20. avg=20.
    //   At t2: window (t1, t2]: only t2=30. avg=30.
    // max_over_time (outer window (t0,t2]) of [10,20,30]: t0 excluded, t1=20 and t2=30 → max=30
    var iv = (InstantVectorResult) eval(ev,
        "max_over_time(avg_over_time(cpu_usage[1m])[2m:1m])", cm.t2, cm.t2, cm.step);
    assertEquals(30f, findValue(iv, cm.cpuUsageApiI1, cm.t2), 1e-4);
  }

  @Test
  void subquery_producesInstantVectorType() throws EvaluationException {
    var cm = TestFixtures.buildCommonMocks();
    var ev = new ExpressionEvaluator(cm.client, cm.discovery, Executors.newFixedThreadPool(2), new MockStatsMerger());
    var res = eval(ev, "avg_over_time(cpu_usage[2m:1m])", cm.t2, cm.t2, cm.step);
    assertEquals(ValueType.INSTANT_VECTOR, res.type());
  }
}

/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.okapi.promql.extractor.TimeSeriesExtractor.findValue;

import java.util.Map;
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
 * Arithmetic operators: -, *, %, ^, unary negation, and __name__ stripping.
 * Base fixture at t2: cpu_i1 avg=25, cpu_i2 avg=70.
 */
public class BinaryOpArithmeticTest {

  private static ExpressionResult eval(
      ExpressionEvaluator ev, String q, long start, long end, long step) {
    var parser = new PromQLParser(new CommonTokenStream(new PromQLLexer(CharStreams.fromString(q))));
    return ev.evaluate(q, start, end, step, parser);
  }

  @Test
  void vectorMinusScalar() throws EvaluationException {
    var cm = TestFixtures.buildCommonMocks();
    var ev = new ExpressionEvaluator(cm.client, cm.discovery, Executors.newFixedThreadPool(2), new MockStatsMerger());
    var iv = (InstantVectorResult) eval(ev, "avg_over_time(cpu_usage[2m]) - 10", cm.t2, cm.t2, cm.step);
    // PromQL spec: scalar-vector arithmetic drops __name__
    var i1 = new SeriesId("",new Labels(cm.cpuUsageApiI1Tags));
    var i2 = new SeriesId("",new Labels(cm.cpuUsageApiI2Tags));
    assertEquals(15f, findValue(iv, i1, cm.t2), 1e-4);
    assertEquals(60f, findValue(iv, i2, cm.t2), 1e-4);
  }

  @Test
  void scalarTimesVector() throws EvaluationException {
    var cm = TestFixtures.buildCommonMocks();
    var ev = new ExpressionEvaluator(cm.client, cm.discovery, Executors.newFixedThreadPool(2), new MockStatsMerger());
    var iv = (InstantVectorResult) eval(ev, "2 * avg_over_time(cpu_usage[2m])", cm.t2, cm.t2, cm.step);
    // LHS scalar, RHS vector: result drops __name__
    var i1 = new SeriesId("",new Labels(cm.cpuUsageApiI1Tags));
    var i2 = new SeriesId("",new Labels(cm.cpuUsageApiI2Tags));
    assertEquals(50f, findValue(iv, i1, cm.t2), 1e-4);
    assertEquals(140f, findValue(iv, i2, cm.t2), 1e-4);
  }

  @Test
  void vectorModuloScalar() throws EvaluationException {
    var cm = TestFixtures.buildCommonMocks();
    var ev = new ExpressionEvaluator(cm.client, cm.discovery, Executors.newFixedThreadPool(2), new MockStatsMerger());
    var iv = (InstantVectorResult) eval(ev, "avg_over_time(cpu_usage[2m]) % 10", cm.t2, cm.t2, cm.step);
    var i1 = new SeriesId("",new Labels(cm.cpuUsageApiI1Tags));
    var i2 = new SeriesId("",new Labels(cm.cpuUsageApiI2Tags));
    assertEquals(5f, findValue(iv, i1, cm.t2), 1e-4);  // 25 % 10 = 5
    assertEquals(0f, findValue(iv, i2, cm.t2), 1e-4);  // 70 % 10 = 0
  }

  @Test
  void vectorPowerScalar() throws EvaluationException {
    var cm = TestFixtures.buildCommonMocks();
    var ev = new ExpressionEvaluator(cm.client, cm.discovery, Executors.newFixedThreadPool(2), new MockStatsMerger());
    var iv = (InstantVectorResult) eval(ev, "avg_over_time(cpu_usage[2m]) ^ 2", cm.t2, cm.t2, cm.step);
    var i1 = new SeriesId("",new Labels(cm.cpuUsageApiI1Tags));
    var i2 = new SeriesId("",new Labels(cm.cpuUsageApiI2Tags));
    assertEquals(625f, findValue(iv, i1, cm.t2), 1e-4);   // 25^2
    assertEquals(4900f, findValue(iv, i2, cm.t2), 1e-4);  // 70^2
  }

  @Test
  void unaryNegation() throws EvaluationException {
    var cm = TestFixtures.buildCommonMocks();
    var ev = new ExpressionEvaluator(cm.client, cm.discovery, Executors.newFixedThreadPool(2), new MockStatsMerger());
    var iv = (InstantVectorResult) eval(ev, "-avg_over_time(cpu_usage[2m])", cm.t2, cm.t2, cm.step);
    var i1 = new SeriesId("",new Labels(cm.cpuUsageApiI1Tags));
    var i2 = new SeriesId("",new Labels(cm.cpuUsageApiI2Tags));
    assertEquals(-25f, findValue(iv, i1, cm.t2), 1e-4);
    assertEquals(-70f, findValue(iv, i2, cm.t2), 1e-4);
  }

  @Test
  void vectorVectorArithmetic_dropsMetricName() throws EvaluationException {
    var cm = TestFixtures.buildCommonMocks();
    var ev = new ExpressionEvaluator(cm.client, cm.discovery, Executors.newFixedThreadPool(2), new MockStatsMerger());
    // vector-vector: result must have null metric name per PromQL spec
    var iv = (InstantVectorResult) eval(ev,
        "avg_over_time(cpu_usage[2m]) + on(job,instance) avg_over_time(cpu_usage[2m])",
        cm.t2, cm.t2, cm.step);
    for (var s : iv.data()) {
      assertEquals("", s.series().metric(), "arithmetic must drop __name__");
    }
    // Values: 25+25=50 for i1, 70+70=140 for i2
    var i1 = new SeriesId("",new Labels(cm.cpuUsageApiI1Tags));
    var i2 = new SeriesId("",new Labels(cm.cpuUsageApiI2Tags));
    assertEquals(50f, findValue(iv, i1, cm.t2), 1e-4);
    assertEquals(140f, findValue(iv, i2, cm.t2), 1e-4);
  }
}

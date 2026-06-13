/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.eval;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.Executors;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.jupiter.api.Test;
import org.okapi.promql.MockStatsMerger;
import org.okapi.promql.TestFixtures;
import org.okapi.promql.eval.exceptions.EvaluationException;
import org.okapi.promql.parser.PromQLLexer;
import org.okapi.promql.parser.PromQLParser;

/**
 * IEEE 754 special values per PromQL spec:
 * - nonzero / 0 = ±Infinity (not NaN)
 * - 0 / 0 = NaN
 * - NaN propagates through arithmetic
 */
public class SpecialValuesTest {

  private static ExpressionResult eval(
      ExpressionEvaluator ev, String q, long start, long end, long step) {
    var parser = new PromQLParser(new CommonTokenStream(new PromQLLexer(CharStreams.fromString(q))));
    return ev.evaluate(q, start, end, step, parser);
  }

  @Test
  void zeroDividedByZero_isNaN_scalar() throws EvaluationException {
    var cm = TestFixtures.buildCommonMocks();
    var ev = new ExpressionEvaluator(cm.client, cm.discovery, Executors.newFixedThreadPool(2), new MockStatsMerger());
    var res = eval(ev, "0 / 0", cm.t2, cm.t2, cm.step);
    assertEquals(ValueType.SCALAR, res.type());
    assertTrue(Double.isNaN(((ScalarResult) res).getValue()), "0/0 must be NaN");
  }

  @Test
  void positiveDividedByZero_isPositiveInfinity_scalar() throws EvaluationException {
    var cm = TestFixtures.buildCommonMocks();
    var ev = new ExpressionEvaluator(cm.client, cm.discovery, Executors.newFixedThreadPool(2), new MockStatsMerger());
    var res = eval(ev, "1 / 0", cm.t2, cm.t2, cm.step);
    assertEquals(ValueType.SCALAR, res.type());
    assertTrue(Double.isInfinite(((ScalarResult) res).getValue()), "1/0 must be Infinite");
    assertTrue(((ScalarResult) res).getValue() > 0, "1/0 must be positive Infinity");
  }

  @Test
  void negativeDividedByZero_isNegativeInfinity_scalar() throws EvaluationException {
    var cm = TestFixtures.buildCommonMocks();
    var ev = new ExpressionEvaluator(cm.client, cm.discovery, Executors.newFixedThreadPool(2), new MockStatsMerger());
    var res = eval(ev, "-1 / 0", cm.t2, cm.t2, cm.step);
    assertEquals(ValueType.SCALAR, res.type());
    assertTrue(Double.isInfinite(((ScalarResult) res).getValue()), "-1/0 must be Infinite");
    assertTrue(((ScalarResult) res).getValue() < 0, "-1/0 must be negative Infinity");
  }

  @Test
  void vectorDividedByZero_isPositiveInfinity() throws EvaluationException {
    var cm = TestFixtures.buildCommonMocks();
    var ev = new ExpressionEvaluator(cm.client, cm.discovery, Executors.newFixedThreadPool(2), new MockStatsMerger());
    var iv = (InstantVectorResult) eval(ev, "avg_over_time(cpu_usage[2m]) / 0", cm.t2, cm.t2, cm.step);
    // cpu values are positive → positive / 0 = +Inf
    for (var s : iv.data()) {
      assertTrue(Double.isInfinite(s.sample().value()), "positive/0 must be +Inf");
      assertTrue(s.sample().value() > 0, "positive/0 must be positive Inf");
    }
  }

  @Test
  void nanPropagates_throughMultiplication() throws EvaluationException {
    var cm = TestFixtures.buildCommonMocks();
    var ev = new ExpressionEvaluator(cm.client, cm.discovery, Executors.newFixedThreadPool(2), new MockStatsMerger());
    // 0 * (v / 0) = 0 * +Inf = NaN per IEEE 754
    var iv = (InstantVectorResult) eval(ev, "0 * (avg_over_time(cpu_usage[2m]) / 0)", cm.t2, cm.t2, cm.step);
    for (var s : iv.data()) {
      assertTrue(Double.isNaN(s.sample().value()), "0 * Inf must propagate NaN");
    }
  }
}

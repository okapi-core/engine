/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.eval;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.Executors;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.jupiter.api.Test;
import org.okapi.promql.MockStatsMerger;
import org.okapi.promql.TestFixtures;
import org.okapi.promql.eval.exceptions.EvaluationException;
import org.okapi.promql.parser.PromQLLexer;
import org.okapi.promql.parser.PromQLParser;

public class TypeCheckTest {

  private static ExpressionResult eval(
      ExpressionEvaluator ev, String q, long start, long end, long step) {
    var parser = new PromQLParser(new CommonTokenStream(new PromQLLexer(CharStreams.fromString(q))));
    return ev.evaluate(q, start, end, step, parser);
  }

  @Test
  void avgOverTime_rejectsScalarArgument() {
    var cm = TestFixtures.buildCommonMocks();
    var ev = new ExpressionEvaluator(cm.client, cm.discovery, Executors.newFixedThreadPool(2), new MockStatsMerger());
    // avg_over_time expects a range-vector; 5 is a scalar → type error
    assertThrows(
        EvaluationException.class,
        () -> eval(ev, "avg_over_time(5)", cm.t2, cm.t2, cm.step));
  }

  @Test
  void clampMin_rejectsRangeVectorArgument() {
    var cm = TestFixtures.buildCommonMocks();
    var ev = new ExpressionEvaluator(cm.client, cm.discovery, Executors.newFixedThreadPool(2), new MockStatsMerger());
    // clamp_min expects instant-vector as first arg; cpu_usage[2m] is a range-vector → type error
    assertThrows(
        EvaluationException.class,
        () -> eval(ev, "clamp_min(cpu_usage[2m], 5)", cm.t2, cm.t2, cm.step));
  }

  @Test
  void scalar_rejectsScalarArgument() {
    var cm = TestFixtures.buildCommonMocks();
    var ev = new ExpressionEvaluator(cm.client, cm.discovery, Executors.newFixedThreadPool(2), new MockStatsMerger());
    // scalar() expects instant-vector; 5 is a scalar → type error
    assertThrows(
        EvaluationException.class,
        () -> eval(ev, "scalar(5)", cm.t2, cm.t2, cm.step));
  }

  @Test
  void setOps_rejectScalarOperands() {
    var cm = TestFixtures.buildCommonMocks();
    var ev = new ExpressionEvaluator(cm.client, cm.discovery, Executors.newFixedThreadPool(2), new MockStatsMerger());
    // and/or/unless require instant vectors; scalar op scalar is not allowed
    assertThrows(
        EvaluationException.class,
        () -> eval(ev, "1 and 1", cm.t2, cm.t2, cm.step));
  }
}

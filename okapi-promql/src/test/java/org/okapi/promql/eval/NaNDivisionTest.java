/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.Executors;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.jupiter.api.Test;
import org.okapi.promql.MockStatsMerger;
import org.okapi.promql.TestFixtures;
import org.okapi.promql.eval.exceptions.EvaluationException;
import org.okapi.promql.parser.PromQLLexer;
import org.okapi.promql.parser.PromQLParser;

public class NaNDivisionTest {

  @Test
  void divisionByZero_isInfinity_perSample() throws EvaluationException {
    var cm = TestFixtures.buildCommonMocks();
    var exec = Executors.newFixedThreadPool(2);
    var merger = new MockStatsMerger();
    var evaluator = new ExpressionEvaluator(cm.client, cm.discovery, exec, merger);

    long t = cm.t2;

    // avg_over_time(cpu_usage[2m]) / 0
    String q = "avg_over_time(cpu_usage[2m]) / 0";
    var parser =
        new PromQLParser(new CommonTokenStream(new PromQLLexer(CharStreams.fromString(q))));
    var res = evaluator.evaluate(q, t, t, cm.step, parser);

    assertEquals(ValueType.INSTANT_VECTOR, res.type());
    var iv = (InstantVectorResult) res;
    // PromQL / IEEE 754: nonzero / 0 = +Inf (not NaN). Only 0/0 = NaN.
    for (var s : iv.data()) {
      assertTrue(Float.isInfinite(s.sample().value()));
    }
  }
}

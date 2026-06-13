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

public class EmptyWindowTest {
  @Test
  void avgOverTime_emptyWindow_isNaN() throws EvaluationException {
    var cm = TestFixtures.buildCommonMocks();
    var exec = Executors.newFixedThreadPool(2);
    var merger = new MockStatsMerger();
    var evaluator = new ExpressionEvaluator(cm.client, cm.discovery, exec, merger);

    // Choose a window before any data exists
    long start = cm.t0 - 10 * 60_000L;
    long end = start;
    long step = cm.step;

    String q = "avg_over_time(cpu_usage[2m])";
    var parser =
        new PromQLParser(new CommonTokenStream(new PromQLLexer(CharStreams.fromString(q))));
    var res = evaluator.evaluate(q, start, end, step, parser);

    assertEquals(ValueType.INSTANT_VECTOR, res.type());
    var iv = (InstantVectorResult) res;
    // No series should appear at all due to instantization/staleness; or if present, values should
    // be NaN.
    // Be tolerant: if engine emits sample, ensure NaN; else zero size is acceptable.
    if (!iv.data().isEmpty()) {
      assertTrue(Double.isNaN(iv.data().get(0).sample().value()));
    }
  }
}

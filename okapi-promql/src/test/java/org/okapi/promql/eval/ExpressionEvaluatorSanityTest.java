/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.concurrent.Executors;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.jupiter.api.Test;
import org.okapi.promql.MockStatsMerger;
import org.okapi.promql.MockTimeSeriesClient;
import org.okapi.promql.NoopSeriesDiscovery;
import org.okapi.promql.eval.exceptions.EvaluationException;
import org.okapi.promql.parser.PromQLLexer;
import org.okapi.promql.parser.PromQLParser;

public class ExpressionEvaluatorSanityTest {

  @Test
  void scalarArithmetic_parsesAndEvaluates() throws EvaluationException {
    var client = new MockTimeSeriesClient(); // not used in this test
    var discovery = new NoopSeriesDiscovery();
    var exec = Executors.newFixedThreadPool(2);

    var merger = new MockStatsMerger();
    var evaluator = new ExpressionEvaluator(client, discovery, exec, merger);

    // Build a parser for: 2 + 3 * 4  (should be 14)
    String promql = "2 + 3 * 4";
    var lexer = new PromQLLexer(CharStreams.fromString(promql));
    var tokens = new CommonTokenStream(lexer);
    var parser = new PromQLParser(tokens);

    long now = System.currentTimeMillis();
    long start = now - 60_000; // arbitrary
    long end = now;
    long step = 15_000;

    // Act
    var result = evaluator.evaluate(promql, start, end, step, parser);

    // Assert
    assertNotNull(result);
    assertEquals(ValueType.SCALAR, result.type());
    double v = ((ScalarResult) result).value;
    assertEquals(14.0f, v, 1e-6, "2 + 3 * 4 should evaluate to 14");
  }
}

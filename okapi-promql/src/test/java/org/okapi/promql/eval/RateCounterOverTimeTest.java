/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.eval;

import static org.junit.jupiter.api.Assertions.*;
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

public class RateCounterOverTimeTest {

  @Test
  void rate_httpRequestsCounter_2m_minutely() throws EvaluationException {
    var cm = TestFixtures.buildCommonMocks();

    var exec = Executors.newFixedThreadPool(2);
    var merger = new MockStatsMerger();
    var evaluator = new ExpressionEvaluator(cm.client, cm.discovery, exec, merger);

    // Query
    String promql = "rate(http_requests_counter[2m])";
    var lexer = new PromQLLexer(CharStreams.fromString(promql));
    var tokens = new CommonTokenStream(lexer);
    var parser = new PromQLParser(tokens);

    // Evaluate from t1..t3 with 1m step
    long start = cm.t1, end = cm.t3, step = cm.step;

    var res = evaluator.evaluate(promql, start, end, step, parser);
    assertEquals(ValueType.INSTANT_VECTOR, res.type());

    var iv = (InstantVectorResult) res;

    // Each window is 2 minutes: (t-2m, t]
    // Buckets (events/min): t0=60, t1=120, t2=180, t3=240
    // t1 window -> {t0,t1} sum=180 over 120s => 1.5/s
    // t2 window -> {t1,t2} sum=300 over 120s => 2.5/s
    // t3 window -> {t2,t3} sum=420 over 120s => 3.5/s
    var http = new SeriesId("", new Labels(cm.httpRequestsCounterApiTags));
    float r1 = findValue(iv, http, cm.t1);
    float r2 = findValue(iv, http, cm.t2);
    float r3 = findValue(iv, http, cm.t3);

    assertEquals(1.5f, r1, 1e-4);
    assertEquals(2.5f, r2, 1e-4);
    assertEquals(3.5f, r3, 1e-4);
  }
}

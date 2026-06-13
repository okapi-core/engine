/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.okapi.promql.extractor.TimeSeriesExtractor.findValue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.jupiter.api.Test;
import org.okapi.promql.MockSeriesDiscovery;
import org.okapi.promql.MockStatsMerger;
import org.okapi.promql.MockTimeSeriesClient;
import org.okapi.promql.eval.VectorData.*;
import org.okapi.promql.eval.exceptions.EvaluationException;
import org.okapi.promql.parser.PromQLLexer;
import org.okapi.promql.parser.PromQLParser;

public class AvgOverTimeTest {

  @Test
  void avgOverTime_httpRequests_2m_singleSeries() throws EvaluationException {
    // --- Common mock data (series + buckets) ---
    // Series: http_requests{job="api"}
    Map<String, String> tags = Map.of("job", "api");
    SeriesId series = new SeriesId("http_requests", new Labels(tags));

    var discovery = new MockSeriesDiscovery(List.of(series));
    var client = new MockTimeSeriesClient();

    // Create 1-minute buckets with values: t0=10, t1=20, t2=30, t3=40
    long t0 = 1_700_000_000_000L; // arbitrary fixed base
    long step = 60_000L; // 1 minute
    long t1 = t0 + step;
    long t2 = t1 + step;
    long t3 = t2 + step;

    client.put("http_requests", tags, t0, 10f);
    client.put("http_requests", tags, t1, 20f);
    client.put("http_requests", tags, t2, 30f);
    client.put("http_requests", tags, t3, 40f);

    // Window = 2m; Evaluate from t1..t3 with 1m steps.
    long start = t1;
    long end = t3;

    // --- Wire evaluator with real components ---
    var exec = Executors.newFixedThreadPool(2);
    var merger = new MockStatsMerger(); // provided no-arg implementation
    var evaluator = new ExpressionEvaluator(client, discovery, exec, merger);

    // --- Parse query ---
    String promql = "avg_over_time(http_requests[2m])";
    var lexer = new PromQLLexer(CharStreams.fromString(promql));
    var tokens = new CommonTokenStream(lexer);
    var parser = new PromQLParser(tokens);

    // --- Evaluate ---
    var result = evaluator.evaluate(promql, start, end, step, parser);

    // --- Assert ---
    assertNotNull(result);
    assertEquals(
        ValueType.INSTANT_VECTOR, result.type(), "avg_over_time returns an instant vector");

    var iv = (InstantVectorResult) result;
    // Expect samples at t1, t2, t3 for our single series:
    // t1 window: [t1-2m, t1] includes t0, t1 => avg(10, 20) = 15
    // t2 window: includes t1, t2 => avg(20, 30) = 25
    // t3 window: includes t2, t3 => avg(30, 40) = 35
    var strippedSeries = new SeriesId("", new Labels(tags));
    double v1 = findValue(iv, strippedSeries, t1);
    double v2 = findValue(iv, strippedSeries, t2);
    double v3 = findValue(iv, strippedSeries, t3);

    assertEquals(15f, v1, 1e-4);
    assertEquals(25f, v2, 1e-4);
    assertEquals(35f, v3, 1e-4);
  }
}

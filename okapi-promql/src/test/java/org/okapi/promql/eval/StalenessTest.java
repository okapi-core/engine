/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.eval;

import static org.junit.jupiter.api.Assertions.*;

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

/**
 * Instant selector staleness window: a data point at time P is matched by instant
 * selector eval at time T only if P > T - 5m AND P <= T.
 * The boundary at exactly T - 5m is exclusive (point is NOT included).
 */
public class StalenessTest {

  private static final long STALENESS_MS = 5 * 60_000L;

  private static ExpressionResult eval(
      ExpressionEvaluator ev, String q, long start, long end, long step) {
    var parser = new PromQLParser(new CommonTokenStream(new PromQLLexer(CharStreams.fromString(q))));
    return ev.evaluate(q, start, end, step, parser);
  }

  @Test
  void dataWithinStalenessWindow_isIncluded() throws EvaluationException {
    var client = new MockTimeSeriesClient();
    var tags = Map.of("job", "test");
    var series = new SeriesId("g", new Labels(tags));
    long dataTs = 1_000_000L;
    client.put("g", tags, dataTs, 42f);
    var discovery = new MockSeriesDiscovery(List.of(series));
    var ev = new ExpressionEvaluator(client, discovery, Executors.newFixedThreadPool(2), new MockStatsMerger());

    // Eval at dataTs + (5m - 1ms): data is 1ms inside the staleness window
    long evalTs = dataTs + STALENESS_MS - 1L;
    var iv = (InstantVectorResult) eval(ev, "g", evalTs, evalTs, 60_000L);
    assertEquals(1, iv.data().size(), "data just inside staleness window must be found");
    assertEquals(42f, iv.data().get(0).sample().value(), 1e-4);
  }

  @Test
  void dataAtExactStalenessBoundary_isExcluded() throws EvaluationException {
    var client = new MockTimeSeriesClient();
    var tags = Map.of("job", "test");
    var series = new SeriesId("g", new Labels(tags));
    long dataTs = 1_000_000L;
    client.put("g", tags, dataTs, 42f);
    var discovery = new MockSeriesDiscovery(List.of(series));
    var ev = new ExpressionEvaluator(client, discovery, Executors.newFixedThreadPool(2), new MockStatsMerger());

    // Eval at dataTs + 5m: data is exactly at the boundary (exclusive) → not found
    long evalTs = dataTs + STALENESS_MS;
    var iv = (InstantVectorResult) eval(ev, "g", evalTs, evalTs, 60_000L);
    assertEquals(0, iv.data().size(), "data at exact staleness boundary must be excluded");
  }

  @Test
  void dataOutsideStalenessWindow_isExcluded() throws EvaluationException {
    var client = new MockTimeSeriesClient();
    var tags = Map.of("job", "test");
    var series = new SeriesId("g", new Labels(tags));
    long dataTs = 1_000_000L;
    client.put("g", tags, dataTs, 42f);
    var discovery = new MockSeriesDiscovery(List.of(series));
    var ev = new ExpressionEvaluator(client, discovery, Executors.newFixedThreadPool(2), new MockStatsMerger());

    // Eval at dataTs + 5m + 1ms: data is 1ms outside the staleness window
    long evalTs = dataTs + STALENESS_MS + 1L;
    var iv = (InstantVectorResult) eval(ev, "g", evalTs, evalTs, 60_000L);
    assertEquals(0, iv.data().size(), "data outside staleness window must be excluded");
  }

  @Test
  void futureData_isExcluded() throws EvaluationException {
    var client = new MockTimeSeriesClient();
    var tags = Map.of("job", "test");
    var series = new SeriesId("g", new Labels(tags));
    long evalTs = 1_000_000L;
    client.put("g", tags, evalTs + 1L, 42f);  // data in the future
    var discovery = new MockSeriesDiscovery(List.of(series));
    var ev = new ExpressionEvaluator(client, discovery, Executors.newFixedThreadPool(2), new MockStatsMerger());

    var iv = (InstantVectorResult) eval(ev, "g", evalTs, evalTs, 60_000L);
    assertEquals(0, iv.data().size(), "future data must be excluded");
  }
}

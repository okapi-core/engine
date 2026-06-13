/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
 * The @ modifier pins a selector to a fixed Unix timestamp in seconds.
 * We use small timestamps (hundreds of seconds) to avoid double-precision loss when
 * the parser converts the literal to double and NodeEvaluator converts back to ms.
 */
public class AtModifierTest {

  private static ExpressionResult eval(
      ExpressionEvaluator ev, String q, long start, long end, long step) {
    var parser = new PromQLParser(new CommonTokenStream(new PromQLLexer(CharStreams.fromString(q))));
    return ev.evaluate(q, start, end, step, parser);
  }

  @Test
  void atModifier_fixesEvalAtSpecifiedTimestamp() throws EvaluationException {
    // Use small timestamps (in ms) so t/1000 is exactly representable as double.
    long step = 60_000L;
    long t0 = 60_000L;   // 60 s
    long t1 = 120_000L;  // 120 s
    long t2 = 180_000L;  // 180 s  ← @ target
    long t3 = 240_000L;  // 240 s

    var client = new MockTimeSeriesClient();
    var tags = Map.of("job", "test");
    var series = new SeriesId("cpu_gauge", new Labels(tags));
    client.put("cpu_gauge", tags, t0, 10f);
    client.put("cpu_gauge", tags, t1, 20f);
    client.put("cpu_gauge", tags, t2, 30f);
    client.put("cpu_gauge", tags, t3, 40f);
    var discovery = new MockSeriesDiscovery(List.of(series));
    var ev = new ExpressionEvaluator(client, discovery, Executors.newFixedThreadPool(2), new MockStatsMerger());

    // @ 180 forces evaluation at t2=180s regardless of start/end.
    // avg_over_time window (t2-2m, t2] = (60s, 180s]: contains t1=20 and t2=30 → avg=25.
    String q = "avg_over_time(cpu_gauge[2m]) @ 180";
    var res = eval(ev, q, t0, t3, step);
    assertEquals(ValueType.INSTANT_VECTOR, res.type());
    var iv = (InstantVectorResult) res;

    // Result must be at t2 (180000 ms) only — @ overrides the outer range to a single point.
    assertEquals(1, iv.data().size());
    assertEquals(t2, iv.data().get(0).sample().ts());
    assertEquals(25f, iv.data().get(0).sample().value(), 1e-4);
  }

  @Test
  void atModifier_evaluatesAtDifferentTimeFromQueryRange() throws EvaluationException {
    long step = 60_000L;
    long t0 = 60_000L;
    long t1 = 120_000L;
    long t2 = 180_000L;
    long t3 = 240_000L;

    var client = new MockTimeSeriesClient();
    var tags = Map.of("job", "test");
    var series = new SeriesId("cpu_gauge", new Labels(tags));
    client.put("cpu_gauge", tags, t0, 10f);
    client.put("cpu_gauge", tags, t1, 20f);
    client.put("cpu_gauge", tags, t2, 30f);
    client.put("cpu_gauge", tags, t3, 40f);
    var discovery = new MockSeriesDiscovery(List.of(series));
    var ev = new ExpressionEvaluator(client, discovery, Executors.newFixedThreadPool(2), new MockStatsMerger());

    // Evaluate "normally" from t1 to t1 (would give window {t0,t1} → avg=15).
    // But @ 180 overrides to t2 (window {t1,t2} → avg=25). Values differ, confirming @ works.
    String q = "avg_over_time(cpu_gauge[2m]) @ 180";
    var resAt = (InstantVectorResult) eval(ev, q, t1, t1, step);
    assertEquals(25f, resAt.data().get(0).sample().value(), 1e-4);  // forced to t2

    // Without @, at t1 we get avg=15.
    var resNormal = (InstantVectorResult) eval(ev, "avg_over_time(cpu_gauge[2m])", t1, t1, step);
    assertEquals(15f, resNormal.data().get(0).sample().value(), 1e-4);
  }
}

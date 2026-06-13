/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * Aggregation operators with by/without clauses.
 * Base fixture at t2: cpu_i1 avg=25, cpu_i2 avg=70.
 */
public class AggregationTest {

  private static ExpressionResult eval(
      ExpressionEvaluator ev, String q, long start, long end, long step) {
    var parser = new PromQLParser(new CommonTokenStream(new PromQLLexer(CharStreams.fromString(q))));
    return ev.evaluate(q, start, end, step, parser);
  }

  @Test
  void sum_by_job() throws EvaluationException {
    var cm = TestFixtures.buildCommonMocks();
    var ev = new ExpressionEvaluator(cm.client, cm.discovery, Executors.newFixedThreadPool(2), new MockStatsMerger());
    var iv = (InstantVectorResult) eval(ev, "sum by(job) (avg_over_time(cpu_usage[2m]))", cm.t2, cm.t2, cm.step);
    assertEquals(1, iv.data().size());
    var id = new SeriesId("sum", new Labels(Map.of("job", "api")));
    assertEquals(95f, findValue(iv, id, cm.t2), 1e-4);  // 25+70
  }

  @Test
  void avg_without_instance() throws EvaluationException {
    var cm = TestFixtures.buildCommonMocks();
    var ev = new ExpressionEvaluator(cm.client, cm.discovery, Executors.newFixedThreadPool(2), new MockStatsMerger());
    var iv = (InstantVectorResult) eval(ev, "avg without(instance) (avg_over_time(cpu_usage[2m]))", cm.t2, cm.t2, cm.step);
    assertEquals(1, iv.data().size());
    var id = new SeriesId("avg", new Labels(Map.of("job", "api")));
    assertEquals(47.5f, findValue(iv, id, cm.t2), 1e-4);  // (25+70)/2
  }

  @Test
  void min_by_job() throws EvaluationException {
    var cm = TestFixtures.buildCommonMocks();
    var ev = new ExpressionEvaluator(cm.client, cm.discovery, Executors.newFixedThreadPool(2), new MockStatsMerger());
    var iv = (InstantVectorResult) eval(ev, "min by(job) (avg_over_time(cpu_usage[2m]))", cm.t2, cm.t2, cm.step);
    var id = new SeriesId("min", new Labels(Map.of("job", "api")));
    assertEquals(25f, findValue(iv, id, cm.t2), 1e-4);
  }

  @Test
  void max_by_job() throws EvaluationException {
    var cm = TestFixtures.buildCommonMocks();
    var ev = new ExpressionEvaluator(cm.client, cm.discovery, Executors.newFixedThreadPool(2), new MockStatsMerger());
    var iv = (InstantVectorResult) eval(ev, "max by(job) (avg_over_time(cpu_usage[2m]))", cm.t2, cm.t2, cm.step);
    var id = new SeriesId("max", new Labels(Map.of("job", "api")));
    assertEquals(70f, findValue(iv, id, cm.t2), 1e-4);
  }

  @Test
  void count_by_job() throws EvaluationException {
    var cm = TestFixtures.buildCommonMocks();
    var ev = new ExpressionEvaluator(cm.client, cm.discovery, Executors.newFixedThreadPool(2), new MockStatsMerger());
    var iv = (InstantVectorResult) eval(ev, "count by(job) (avg_over_time(cpu_usage[2m]))", cm.t2, cm.t2, cm.step);
    var id = new SeriesId("count", new Labels(Map.of("job", "api")));
    assertEquals(2f, findValue(iv, id, cm.t2), 1e-4);
  }

  @Test
  void topk_returnsKHighestSeries() throws EvaluationException {
    var cm = TestFixtures.buildCommonMocks();
    var ev = new ExpressionEvaluator(cm.client, cm.discovery, Executors.newFixedThreadPool(2), new MockStatsMerger());
    var iv = (InstantVectorResult) eval(ev, "topk(1, avg_over_time(cpu_usage[2m]))", cm.t2, cm.t2, cm.step);
    assertEquals(1, iv.data().size());
    assertEquals(70f, iv.data().get(0).sample().value(), 1e-4);  // i2 is highest
  }

  @Test
  void bottomk_returnsKLowestSeries() throws EvaluationException {
    var cm = TestFixtures.buildCommonMocks();
    var ev = new ExpressionEvaluator(cm.client, cm.discovery, Executors.newFixedThreadPool(2), new MockStatsMerger());
    var iv = (InstantVectorResult) eval(ev, "bottomk(1, avg_over_time(cpu_usage[2m]))", cm.t2, cm.t2, cm.step);
    assertEquals(1, iv.data().size());
    assertEquals(25f, iv.data().get(0).sample().value(), 1e-4);  // i1 is lowest
  }

  @Test
  void quantile_median_by_job() throws EvaluationException {
    var cm = TestFixtures.buildCommonMocks();
    var ev = new ExpressionEvaluator(cm.client, cm.discovery, Executors.newFixedThreadPool(2), new MockStatsMerger());
    // q=0.5 of [25, 70] → 25*0.5 + 70*0.5 = 47.5
    var iv = (InstantVectorResult) eval(ev, "quantile by(job) (0.5, avg_over_time(cpu_usage[2m]))", cm.t2, cm.t2, cm.step);
    var id = new SeriesId("quantile", new Labels(Map.of("job", "api")));
    assertEquals(47.5f, findValue(iv, id, cm.t2), 1e-4);
  }
}

/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

/**
 * Window stats at t2, 2-minute range: window (t0,t2] contains {t1=20, t2=30} for cpu_i1.
 */
public class WindowStatsTest {

  private static ExpressionResult eval(
      ExpressionEvaluator ev, String q, long start, long end, long step) {
    var parser = new PromQLParser(new CommonTokenStream(new PromQLLexer(CharStreams.fromString(q))));
    return ev.evaluate(q, start, end, step, parser);
  }

  @Test
  void min_over_time() throws EvaluationException {
    var cm = TestFixtures.buildCommonMocks();
    var evaluator =
        new ExpressionEvaluator(
            cm.client, cm.discovery, Executors.newFixedThreadPool(2), new MockStatsMerger());
    var iv = (InstantVectorResult) eval(evaluator, "min_over_time(cpu_usage[2m])", cm.t2, cm.t2, cm.step);
    assertEquals(20f, findValue(iv, new SeriesId("", new Labels(cm.cpuUsageApiI1Tags)), cm.t2), 1e-4);
  }

  @Test
  void max_over_time() throws EvaluationException {
    var cm = TestFixtures.buildCommonMocks();
    var evaluator =
        new ExpressionEvaluator(
            cm.client, cm.discovery, Executors.newFixedThreadPool(2), new MockStatsMerger());
    var iv = (InstantVectorResult) eval(evaluator, "max_over_time(cpu_usage[2m])", cm.t2, cm.t2, cm.step);
    assertEquals(30f, findValue(iv, new SeriesId("", new Labels(cm.cpuUsageApiI1Tags)), cm.t2), 1e-4);
  }

  @Test
  void sum_over_time() throws EvaluationException {
    var cm = TestFixtures.buildCommonMocks();
    var evaluator =
        new ExpressionEvaluator(
            cm.client, cm.discovery, Executors.newFixedThreadPool(2), new MockStatsMerger());
    var iv = (InstantVectorResult) eval(evaluator, "sum_over_time(cpu_usage[2m])", cm.t2, cm.t2, cm.step);
    assertEquals(50f, findValue(iv, new SeriesId("", new Labels(cm.cpuUsageApiI1Tags)), cm.t2), 1e-4);
  }

  @Test
  void count_over_time() throws EvaluationException {
    var cm = TestFixtures.buildCommonMocks();
    var evaluator =
        new ExpressionEvaluator(
            cm.client, cm.discovery, Executors.newFixedThreadPool(2), new MockStatsMerger());
    var iv = (InstantVectorResult) eval(evaluator, "count_over_time(cpu_usage[2m])", cm.t2, cm.t2, cm.step);
    assertEquals(2f, findValue(iv, new SeriesId("", new Labels(cm.cpuUsageApiI1Tags)), cm.t2), 1e-4);
  }

  @Test
  void last_over_time() throws EvaluationException {
    var cm = TestFixtures.buildCommonMocks();
    var evaluator =
        new ExpressionEvaluator(
            cm.client, cm.discovery, Executors.newFixedThreadPool(2), new MockStatsMerger());
    var iv = (InstantVectorResult) eval(evaluator, "last_over_time(cpu_usage[2m])", cm.t2, cm.t2, cm.step);
    assertEquals(30f, findValue(iv, new SeriesId("", new Labels(cm.cpuUsageApiI1Tags)), cm.t2), 1e-4);
  }

  @Test
  void present_over_time_returnsOne_whenDataExists() throws EvaluationException {
    var cm = TestFixtures.buildCommonMocks();
    var evaluator =
        new ExpressionEvaluator(
            cm.client, cm.discovery, Executors.newFixedThreadPool(2), new MockStatsMerger());
    var iv = (InstantVectorResult) eval(evaluator, "present_over_time(cpu_usage[2m])", cm.t2, cm.t2, cm.step);
    assertEquals(1f, findValue(iv, new SeriesId("", new Labels(cm.cpuUsageApiI1Tags)), cm.t2), 1e-4);
  }

  @Test
  void quantile_over_time_median() throws EvaluationException {
    var cm = TestFixtures.buildCommonMocks();
    var evaluator =
        new ExpressionEvaluator(
            cm.client, cm.discovery, Executors.newFixedThreadPool(2), new MockStatsMerger());
    // Window {20, 30}: q=0.5, rank=0.5*(2-1)=0.5 → 20 + 0.5*(30-20) = 25
    var iv = (InstantVectorResult) eval(evaluator, "quantile_over_time(0.5, cpu_usage[2m])", cm.t2, cm.t2, cm.step);
    assertEquals(25f, findValue(iv, new SeriesId("", new Labels(cm.cpuUsageApiI1Tags)), cm.t2), 1e-4);
  }
}

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
 * Instant-vector functions: abs, ceil, floor, round, clamp_min/max, sort/sort_desc.
 * Base fixture at t2: cpu_i1 avg=25, cpu_i2 avg=70.
 */
public class InstantFunctionsTest {

  private static ExpressionResult eval(
      ExpressionEvaluator ev, String q, long start, long end, long step) {
    var parser = new PromQLParser(new CommonTokenStream(new PromQLLexer(CharStreams.fromString(q))));
    return ev.evaluate(q, start, end, step, parser);
  }

  @Test
  void abs_preservesPositiveValues() throws EvaluationException {
    var cm = TestFixtures.buildCommonMocks();
    var ev = new ExpressionEvaluator(cm.client, cm.discovery, Executors.newFixedThreadPool(2), new MockStatsMerger());
    var iv = (InstantVectorResult) eval(ev, "abs(avg_over_time(cpu_usage[2m]))", cm.t2, cm.t2, cm.step);
    assertEquals(25f, findValue(iv, new SeriesId("", new Labels(cm.cpuUsageApiI1Tags)), cm.t2), 1e-4);
    assertEquals(70f, findValue(iv, new SeriesId("", new Labels(cm.cpuUsageApiI2Tags)), cm.t2), 1e-4);
  }

  @Test
  void ceil_roundsUpFractional() throws EvaluationException {
    var cm = TestFixtures.buildCommonMocks();
    var ev = new ExpressionEvaluator(cm.client, cm.discovery, Executors.newFixedThreadPool(2), new MockStatsMerger());
    // 25/3 = 8.333... → ceil = 9; 70/3 = 23.333... → ceil = 24
    // division drops __name__, so look up by null-metric SeriesId
    var iv = (InstantVectorResult) eval(ev, "ceil(avg_over_time(cpu_usage[2m]) / 3)", cm.t2, cm.t2, cm.step);
    var i1 = new SeriesId("",new Labels(cm.cpuUsageApiI1Tags));
    var i2 = new SeriesId("",new Labels(cm.cpuUsageApiI2Tags));
    assertEquals(9f, findValue(iv, i1, cm.t2), 1e-4);
    assertEquals(24f, findValue(iv, i2, cm.t2), 1e-4);
  }

  @Test
  void floor_roundsDownFractional() throws EvaluationException {
    var cm = TestFixtures.buildCommonMocks();
    var ev = new ExpressionEvaluator(cm.client, cm.discovery, Executors.newFixedThreadPool(2), new MockStatsMerger());
    // 25/3 = 8.333... → floor = 8; 70/3 = 23.333... → floor = 23
    var iv = (InstantVectorResult) eval(ev, "floor(avg_over_time(cpu_usage[2m]) / 3)", cm.t2, cm.t2, cm.step);
    var i1 = new SeriesId("",new Labels(cm.cpuUsageApiI1Tags));
    var i2 = new SeriesId("",new Labels(cm.cpuUsageApiI2Tags));
    assertEquals(8f, findValue(iv, i1, cm.t2), 1e-4);
    assertEquals(23f, findValue(iv, i2, cm.t2), 1e-4);
  }

  @Test
  void clamp_min_raisesValuesBelow() throws EvaluationException {
    var cm = TestFixtures.buildCommonMocks();
    var ev = new ExpressionEvaluator(cm.client, cm.discovery, Executors.newFixedThreadPool(2), new MockStatsMerger());
    // clamp_min(25, 40) = 40; clamp_min(70, 40) = 70
    var iv = (InstantVectorResult) eval(ev, "clamp_min(avg_over_time(cpu_usage[2m]), 40)", cm.t2, cm.t2, cm.step);
    assertEquals(40f, findValue(iv, new SeriesId("", new Labels(cm.cpuUsageApiI1Tags)), cm.t2), 1e-4);
    assertEquals(70f, findValue(iv, new SeriesId("", new Labels(cm.cpuUsageApiI2Tags)), cm.t2), 1e-4);
  }

  @Test
  void clamp_max_capsValuesAbove() throws EvaluationException {
    var cm = TestFixtures.buildCommonMocks();
    var ev = new ExpressionEvaluator(cm.client, cm.discovery, Executors.newFixedThreadPool(2), new MockStatsMerger());
    // clamp_max(25, 40) = 25; clamp_max(70, 40) = 40
    var iv = (InstantVectorResult) eval(ev, "clamp_max(avg_over_time(cpu_usage[2m]), 40)", cm.t2, cm.t2, cm.step);
    assertEquals(25f, findValue(iv, new SeriesId("", new Labels(cm.cpuUsageApiI1Tags)), cm.t2), 1e-4);
    assertEquals(40f, findValue(iv, new SeriesId("", new Labels(cm.cpuUsageApiI2Tags)), cm.t2), 1e-4);
  }

  @Test
  void sort_ascendingOrder() throws EvaluationException {
    var cm = TestFixtures.buildCommonMocks();
    var ev = new ExpressionEvaluator(cm.client, cm.discovery, Executors.newFixedThreadPool(2), new MockStatsMerger());
    var iv = (InstantVectorResult) eval(ev, "sort(avg_over_time(cpu_usage[2m]))", cm.t2, cm.t2, cm.step);
    // i1=25 before i2=70
    assertEquals(25f, iv.data().get(0).sample().value(), 1e-4);
    assertEquals(70f, iv.data().get(1).sample().value(), 1e-4);
  }

  @Test
  void sort_desc_descendingOrder() throws EvaluationException {
    var cm = TestFixtures.buildCommonMocks();
    var ev = new ExpressionEvaluator(cm.client, cm.discovery, Executors.newFixedThreadPool(2), new MockStatsMerger());
    var iv = (InstantVectorResult) eval(ev, "sort_desc(avg_over_time(cpu_usage[2m]))", cm.t2, cm.t2, cm.step);
    // i2=70 before i1=25
    assertEquals(70f, iv.data().get(0).sample().value(), 1e-4);
    assertEquals(25f, iv.data().get(1).sample().value(), 1e-4);
  }
}

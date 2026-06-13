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
 * Comparison operators without bool: matching samples are kept with their original value;
 * non-matching samples are filtered out.
 * Base fixture at t2: cpu_i1 avg=25, cpu_i2 avg=70.
 */
public class ComparisonFilterTest {

  private static ExpressionResult eval(
      ExpressionEvaluator ev, String q, long start, long end, long step) {
    var parser = new PromQLParser(new CommonTokenStream(new PromQLLexer(CharStreams.fromString(q))));
    return ev.evaluate(q, start, end, step, parser);
  }

  @Test
  void greaterThan_strictlyFilters() throws EvaluationException {
    var cm = TestFixtures.buildCommonMocks();
    var ev = new ExpressionEvaluator(cm.client, cm.discovery, Executors.newFixedThreadPool(2), new MockStatsMerger());
    // i1=25 > 25 false (filtered), i2=70 > 25 true (kept at 70)
    var iv = (InstantVectorResult) eval(ev, "avg_over_time(cpu_usage[2m]) > 25", cm.t2, cm.t2, cm.step);
    assertEquals(1, iv.data().size());
    assertEquals(70f, findValue(iv, new SeriesId("", new Labels(cm.cpuUsageApiI2Tags)),cm.t2), 1e-4);
  }

  @Test
  void greaterThanOrEqual_includesBoundary() throws EvaluationException {
    var cm = TestFixtures.buildCommonMocks();
    var ev = new ExpressionEvaluator(cm.client, cm.discovery, Executors.newFixedThreadPool(2), new MockStatsMerger());
    // i1=25 >= 25 true (kept at 25), i2=70 >= 25 true (kept at 70)
    var iv = (InstantVectorResult) eval(ev, "avg_over_time(cpu_usage[2m]) >= 25", cm.t2, cm.t2, cm.step);
    assertEquals(2, iv.data().size());
    assertEquals(25f, findValue(iv, new SeriesId("", new Labels(cm.cpuUsageApiI1Tags)),cm.t2), 1e-4);
    assertEquals(70f, findValue(iv, new SeriesId("", new Labels(cm.cpuUsageApiI2Tags)),cm.t2), 1e-4);
  }

  @Test
  void lessThan_filtersHighValues() throws EvaluationException {
    var cm = TestFixtures.buildCommonMocks();
    var ev = new ExpressionEvaluator(cm.client, cm.discovery, Executors.newFixedThreadPool(2), new MockStatsMerger());
    // i1=25 < 50 true, i2=70 < 50 false
    var iv = (InstantVectorResult) eval(ev, "avg_over_time(cpu_usage[2m]) < 50", cm.t2, cm.t2, cm.step);
    assertEquals(1, iv.data().size());
    assertEquals(25f, findValue(iv, new SeriesId("", new Labels(cm.cpuUsageApiI1Tags)),cm.t2), 1e-4);
  }

  @Test
  void equalTo_keepsMatchingSeriesAtOriginalValue() throws EvaluationException {
    var cm = TestFixtures.buildCommonMocks();
    var ev = new ExpressionEvaluator(cm.client, cm.discovery, Executors.newFixedThreadPool(2), new MockStatsMerger());
    // i1=25 == 25 → kept at 25; i2=70 == 25 → filtered
    var iv = (InstantVectorResult) eval(ev, "avg_over_time(cpu_usage[2m]) == 25", cm.t2, cm.t2, cm.step);
    assertEquals(1, iv.data().size());
    assertEquals(25f, findValue(iv, new SeriesId("", new Labels(cm.cpuUsageApiI1Tags)),cm.t2), 1e-4);
  }

  @Test
  void notEqual_filtersMatchingValue() throws EvaluationException {
    var cm = TestFixtures.buildCommonMocks();
    var ev = new ExpressionEvaluator(cm.client, cm.discovery, Executors.newFixedThreadPool(2), new MockStatsMerger());
    // i1=25 != 25 false (filtered), i2=70 != 25 true (kept at 70)
    var iv = (InstantVectorResult) eval(ev, "avg_over_time(cpu_usage[2m]) != 25", cm.t2, cm.t2, cm.step);
    assertEquals(1, iv.data().size());
    assertEquals(70f, findValue(iv, new SeriesId("", new Labels(cm.cpuUsageApiI2Tags)),cm.t2), 1e-4);
  }
}

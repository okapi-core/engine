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

public class UtilFunctionsTest {

  private static ExpressionResult eval(
      ExpressionEvaluator ev, String q, long start, long end, long step) {
    var parser = new PromQLParser(new CommonTokenStream(new PromQLLexer(CharStreams.fromString(q))));
    return ev.evaluate(q, start, end, step, parser);
  }

  @Test
  void absent_returnOneWhenMetricMissing() throws EvaluationException {
    var cm = TestFixtures.buildCommonMocks();
    var ev = new ExpressionEvaluator(cm.client, cm.discovery, Executors.newFixedThreadPool(2), new MockStatsMerger());
    // cpu_usage{instance="missing"} matches no known series
    var iv = (InstantVectorResult) eval(ev, "absent(cpu_usage{instance=\"missing\"})", cm.t2, cm.t2, cm.step);
    assertEquals(1, iv.data().size());
    assertEquals(1f, iv.data().get(0).sample().value(), 1e-4);
  }

  @Test
  void absent_returnsEmptyWhenMetricExists() throws EvaluationException {
    var cm = TestFixtures.buildCommonMocks();
    var ev = new ExpressionEvaluator(cm.client, cm.discovery, Executors.newFixedThreadPool(2), new MockStatsMerger());
    // cpu_usage{instance="i1"} exists
    var iv = (InstantVectorResult) eval(ev, "absent(cpu_usage{instance=\"i1\"})", cm.t2, cm.t2, cm.step);
    assertTrue(iv.data().isEmpty());
  }

  @Test
  void timestamp_returnsUnixSeconds() throws EvaluationException {
    var cm = TestFixtures.buildCommonMocks();
    var ev = new ExpressionEvaluator(cm.client, cm.discovery, Executors.newFixedThreadPool(2), new MockStatsMerger());
    // timestamp() replaces value with sample's unix timestamp in seconds
    var iv = (InstantVectorResult) eval(ev, "timestamp(avg_over_time(cpu_usage[2m]))", cm.t2, cm.t2, cm.step);
    float expected = cm.t2 / 1000f;
    assertEquals(expected, findValue(iv, cm.cpuUsageApiI1, cm.t2), 1e-4);
    assertEquals(expected, findValue(iv, cm.cpuUsageApiI2, cm.t2), 1e-4);
  }

  @Test
  void time_returnsCurrentWallClockAsScalar() throws EvaluationException {
    var cm = TestFixtures.buildCommonMocks();
    var ev = new ExpressionEvaluator(cm.client, cm.discovery, Executors.newFixedThreadPool(2), new MockStatsMerger());
    long beforeMs = System.currentTimeMillis();
    var res = eval(ev, "time()", cm.t2, cm.t2, cm.step);
    long afterMs = System.currentTimeMillis();
    assertEquals(ValueType.SCALAR, res.type());
    float v = ((ScalarResult) res).getValue();
    assertTrue(v >= beforeMs / 1000f && v <= afterMs / 1000f + 1f, "time() should be current wall-clock");
  }

  @Test
  void scalar_singleSeries_returnsItsValue() throws EvaluationException {
    var cm = TestFixtures.buildCommonMocks();
    var ev = new ExpressionEvaluator(cm.client, cm.discovery, Executors.newFixedThreadPool(2), new MockStatsMerger());
    // pod_replicas has 1 series, avg=3 → scalar(IV{1}) = 3
    var res = eval(ev, "scalar(avg_over_time(pod_replicas[2m]))", cm.t2, cm.t2, cm.step);
    assertEquals(ValueType.SCALAR, res.type());
    assertEquals(3f, ((ScalarResult) res).getValue(), 1e-4);
  }

  @Test
  void scalar_multiSeries_returnsNaN() throws EvaluationException {
    var cm = TestFixtures.buildCommonMocks();
    var ev = new ExpressionEvaluator(cm.client, cm.discovery, Executors.newFixedThreadPool(2), new MockStatsMerger());
    // cpu_usage has 2 series → scalar(IV{2}) = NaN
    var res = eval(ev, "scalar(avg_over_time(cpu_usage[2m]))", cm.t2, cm.t2, cm.step);
    assertEquals(ValueType.SCALAR, res.type());
    assertTrue(Float.isNaN(((ScalarResult) res).getValue()));
  }
}

/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

public class RangeFunctionsTest {

  private static ExpressionResult eval(
      ExpressionEvaluator ev, String q, long start, long end, long step) {
    var parser = new PromQLParser(new CommonTokenStream(new PromQLLexer(CharStreams.fromString(q))));
    return ev.evaluate(q, start, end, step, parser);
  }

  @Test
  void irate_counterSeries_lastTwoBuckets() throws EvaluationException {
    var cm = TestFixtures.buildCommonMocks();
    var evaluator =
        new ExpressionEvaluator(
            cm.client, cm.discovery, Executors.newFixedThreadPool(2), new MockStatsMerger());

    // irate uses the last two samples in the range window.
    // At t2, window (t0,t2]: {t1=120, t2=180}
    // irate = last_count / (last_ts - prev_ts) = 180 / 60s = 3.0/s
    var res = eval(evaluator, "irate(http_requests_counter[2m])", cm.t2, cm.t2, cm.step);
    assertEquals(ValueType.INSTANT_VECTOR, res.type());
    var iv = (InstantVectorResult) res;
    assertEquals(3.0f, findValue(iv, cm.httpRequestsCounterApi, cm.t2), 1e-4);
  }

  @Test
  void increase_counterSeries_sumOfBuckets() throws EvaluationException {
    var cm = TestFixtures.buildCommonMocks();
    var evaluator =
        new ExpressionEvaluator(
            cm.client, cm.discovery, Executors.newFixedThreadPool(2), new MockStatsMerger());

    // increase = sum of counts in window (t0,t2]: t1=120 + t2=180 = 300
    var res = eval(evaluator, "increase(http_requests_counter[2m])", cm.t2, cm.t2, cm.step);
    assertEquals(ValueType.INSTANT_VECTOR, res.type());
    var iv = (InstantVectorResult) res;
    assertEquals(300f, findValue(iv, cm.httpRequestsCounterApi, cm.t2), 1e-4);
  }

  @Test
  void delta_gaugeSeries_lastMinusFirst() throws EvaluationException {
    var cm = TestFixtures.buildCommonMocks();
    var evaluator =
        new ExpressionEvaluator(
            cm.client, cm.discovery, Executors.newFixedThreadPool(2), new MockStatsMerger());

    // delta = last - first in window (t0,t2]: first=t1=20, last=t2=30 => delta=10 for i1
    var res = eval(evaluator, "delta(cpu_usage[2m])", cm.t2, cm.t2, cm.step);
    assertEquals(ValueType.INSTANT_VECTOR, res.type());
    var iv = (InstantVectorResult) res;
    assertEquals(10f, findValue(iv, cm.cpuUsageApiI1, cm.t2), 1e-4);
    // i2: first=t1=60, last=t2=80 => delta=20
    assertEquals(20f, findValue(iv, cm.cpuUsageApiI2, cm.t2), 1e-4);
  }

  @Test
  void idelta_gaugeSeries_lastMinusPrev() throws EvaluationException {
    var cm = TestFixtures.buildCommonMocks();
    var evaluator =
        new ExpressionEvaluator(
            cm.client, cm.discovery, Executors.newFixedThreadPool(2), new MockStatsMerger());

    // idelta = last - second-to-last in window (t0,t2]: {t1=20, t2=30} => 30-20=10 for i1
    var res = eval(evaluator, "idelta(cpu_usage[2m])", cm.t2, cm.t2, cm.step);
    assertEquals(ValueType.INSTANT_VECTOR, res.type());
    var iv = (InstantVectorResult) res;
    assertEquals(10f, findValue(iv, cm.cpuUsageApiI1, cm.t2), 1e-4);
  }

  @Test
  void deriv_gaugeSeries_slopePerSecond() throws EvaluationException {
    var cm = TestFixtures.buildCommonMocks();
    var evaluator =
        new ExpressionEvaluator(
            cm.client, cm.discovery, Executors.newFixedThreadPool(2), new MockStatsMerger());

    // deriv = (last - first) / seconds in window (t0,t2]: {t1=20@t1, t2=30@t2}
    // d=10, seconds=(t2-t1)/1000=60 => deriv=10/60=1/6
    var res = eval(evaluator, "deriv(cpu_usage[2m])", cm.t2, cm.t2, cm.step);
    assertEquals(ValueType.INSTANT_VECTOR, res.type());
    var iv = (InstantVectorResult) res;
    assertEquals(1f / 6f, findValue(iv, cm.cpuUsageApiI1, cm.t2), 1e-4);
  }

  @Test
  void gaugeScan_isRequiredFor_delta_idelta_deriv() throws EvaluationException {
    var cm = TestFixtures.buildCommonMocks();
    var evaluator =
        new ExpressionEvaluator(
            cm.client, cm.discovery, Executors.newFixedThreadPool(2), new MockStatsMerger());

    // _counter series are SumScan; delta/idelta/deriv require GaugeScan → returns empty result
    var resDelta = eval(evaluator, "delta(http_requests_counter[2m])", cm.t2, cm.t2, cm.step);
    assertEquals(ValueType.INSTANT_VECTOR, resDelta.type());
    assertTrue(((InstantVectorResult) resDelta).data().isEmpty());
  }
}

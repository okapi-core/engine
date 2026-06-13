/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.eval;

import static org.junit.jupiter.api.Assertions.*;
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
 * __name__ must be dropped in arithmetic results and preserved in set-op and aggregation results.
 */
public class MetricNameStrippingTest {

  private static ExpressionResult eval(
      ExpressionEvaluator ev, String q, long start, long end, long step) {
    var parser = new PromQLParser(new CommonTokenStream(new PromQLLexer(CharStreams.fromString(q))));
    return ev.evaluate(q, start, end, step, parser);
  }

  @Test
  void vectorVectorArithmetic_dropsMetricName() throws EvaluationException {
    var cm = TestFixtures.buildCommonMocks();
    var ev = new ExpressionEvaluator(cm.client, cm.discovery, Executors.newFixedThreadPool(2), new MockStatsMerger());
    var iv = (InstantVectorResult) eval(ev,
        "avg_over_time(cpu_usage[2m]) + on(job,instance) avg_over_time(cpu_usage[2m])",
        cm.t2, cm.t2, cm.step);
    for (var s : iv.data()) {
      assertEquals("", s.series().metric(), "vector-vector arithmetic must drop __name__");
    }
  }

  @Test
  void scalarVectorArithmetic_dropsMetricName() throws EvaluationException {
    var cm = TestFixtures.buildCommonMocks();
    var ev = new ExpressionEvaluator(cm.client, cm.discovery, Executors.newFixedThreadPool(2), new MockStatsMerger());
    // PromQL spec: scalar-vector arithmetic also drops __name__
    var iv = (InstantVectorResult) eval(ev, "avg_over_time(cpu_usage[2m]) + 1", cm.t2, cm.t2, cm.step);
    for (var s : iv.data()) {
      assertEquals("", s.series().metric(), "scalar-vector arithmetic must drop __name__");
    }
  }

  @Test
  void setOp_and_preservesLhsMetricName() throws EvaluationException {
    var cm = TestFixtures.buildCommonMocks();
    var ev = new ExpressionEvaluator(cm.client, cm.discovery, Executors.newFixedThreadPool(2), new MockStatsMerger());
    var iv = (InstantVectorResult) eval(ev,
        "cpu_usage and on(job,instance) avg_over_time(cpu_usage[2m])",
        cm.t2, cm.t2, cm.step);
    for (var s : iv.data()) {
      assertEquals("cpu_usage", s.series().metric(), "set op must preserve LHS __name__");
    }
  }

  @Test
  void aggregation_usesOpNameAsMetric() throws EvaluationException {
    var cm = TestFixtures.buildCommonMocks();
    var ev = new ExpressionEvaluator(cm.client, cm.discovery, Executors.newFixedThreadPool(2), new MockStatsMerger());
    var iv = (InstantVectorResult) eval(ev,
        "sum by(job) (avg_over_time(cpu_usage[2m]))", cm.t2, cm.t2, cm.step);
    assertEquals(1, iv.data().size());
    var id = new SeriesId("sum", new Labels(Map.of("job", "api")));
    assertEquals(95f, findValue(iv, id, cm.t2), 1e-4);
    assertEquals("sum", iv.data().get(0).series().metric());
  }
}

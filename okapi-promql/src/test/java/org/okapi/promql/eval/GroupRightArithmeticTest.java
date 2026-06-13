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

public class GroupRightArithmeticTest {

  private static ExpressionResult eval(
      ExpressionEvaluator ev, String q, long start, long end, long step) {
    var parser = new PromQLParser(new CommonTokenStream(new PromQLLexer(CharStreams.fromString(q))));
    return ev.evaluate(q, start, end, step, parser);
  }

  @Test
  void groupRight_expandsLhsToMatchMultipleRhs() throws EvaluationException {
    var cm = TestFixtures.buildCommonMocks();
    var ev = new ExpressionEvaluator(cm.client, cm.discovery, Executors.newFixedThreadPool(2), new MockStatsMerger());

    // pod_replicas{job=api} (1 series, avg=3) + on(job) group_right(instance) cpu_usage (2 series)
    // LHS: replicas(3). RHS: cpu_i1(25), cpu_i2(70).
    // group_right: LHS size=1, fan out to each RHS match.
    // Result: replicas+cpu_i1=28, replicas+cpu_i2=73.
    // Labels: mergeLabels(replicas, cpu_iX) = replicas_labels + include=[instance]
    //   → {job:api, instance:i1} and {job:api, instance:i2}
    String q =
        "avg_over_time(pod_replicas[2m]) + on (job) group_right (instance) avg_over_time(cpu_usage[2m])";
    var iv = (InstantVectorResult) eval(ev, q, cm.t2, cm.t2, cm.step);

    assertEquals(2, iv.data().size());
    var ri1 = new SeriesId(null, new Labels(Map.of("job", "api", "instance", "i1")));
    var ri2 = new SeriesId(null, new Labels(Map.of("job", "api", "instance", "i2")));
    assertEquals(28f, findValue(iv, ri1, cm.t2), 1e-4);
    assertEquals(73f, findValue(iv, ri2, cm.t2), 1e-4);
  }

  @Test
  void groupRight_errorWhenLhsHasMultipleMatches() throws EvaluationException {
    var cm = TestFixtures.buildCommonMocks();
    var ev = new ExpressionEvaluator(cm.client, cm.discovery, Executors.newFixedThreadPool(2), new MockStatsMerger());

    // cpu_usage (2 series) + on(job) group_right pod_replicas (1 series)
    // With group_right: LHS must have exactly 1 match per key. But cpu has 2 → EvaluationException.
    String q =
        "avg_over_time(cpu_usage[2m]) + on (job) group_right avg_over_time(pod_replicas[2m])";
    try {
      eval(ev, q, cm.t2, cm.t2, cm.step);
      throw new AssertionError("Expected EvaluationException for multi-LHS group_right");
    } catch (EvaluationException expected) {
      // expected: group_right requires exactly one LHS match per key
    }
  }
}

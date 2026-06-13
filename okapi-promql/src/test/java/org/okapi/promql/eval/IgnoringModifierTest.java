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

public class IgnoringModifierTest {

  private static ExpressionResult eval(
      ExpressionEvaluator ev, String q, long start, long end, long step) {
    var parser = new PromQLParser(new CommonTokenStream(new PromQLLexer(CharStreams.fromString(q))));
    return ev.evaluate(q, start, end, step, parser);
  }

  @Test
  void ignoring_groupLeft_arithmetic() throws EvaluationException {
    var cm = TestFixtures.buildCommonMocks();
    var ev = new ExpressionEvaluator(cm.client, cm.discovery, Executors.newFixedThreadPool(2), new MockStatsMerger());

    // cpu_usage (i1,i2) + ignoring(instance) group_left pod_replicas{job=api}
    // Join key (ignoring instance) = {job:api} for all. RHS has 1 match → group_left valid.
    // i1=25+3=28, i2=70+3=73
    String q =
        "avg_over_time(cpu_usage[2m]) + ignoring (instance) group_left avg_over_time(pod_replicas[2m])";
    var iv = (InstantVectorResult) eval(ev, q, cm.t2, cm.t2, cm.step);
    assertEquals(2, iv.data().size());
    var i1 = new SeriesId("",new Labels(cm.cpuUsageApiI1Tags));
    var i2 = new SeriesId("",new Labels(cm.cpuUsageApiI2Tags));
    assertEquals(28f, findValue(iv, i1, cm.t2), 1e-4);
    assertEquals(73f, findValue(iv, i2, cm.t2), 1e-4);
  }

  @Test
  void ignoring_and_filters() throws EvaluationException {
    var cm = TestFixtures.buildCommonMocks();
    var ev = new ExpressionEvaluator(cm.client, cm.discovery, Executors.newFixedThreadPool(2), new MockStatsMerger());

    // cpu_usage and ignoring(instance) mem_usage
    // Both have key {job:api} → i1 and i2 both have a RHS match → both kept.
    String q = "avg_over_time(cpu_usage[2m]) and ignoring (instance) avg_over_time(mem_usage[2m])";
    var iv = (InstantVectorResult) eval(ev, q, cm.t2, cm.t2, cm.step);
    assertEquals(2, iv.data().size());
  }

  @Test
  void ignoring_unless_removesAllWhenRhsCoversIgnoredKey() throws EvaluationException {
    var cm = TestFixtures.buildCommonMocks();
    var ev = new ExpressionEvaluator(cm.client, cm.discovery, Executors.newFixedThreadPool(2), new MockStatsMerger());

    // cpu_usage unless ignoring(instance) mem_usage
    // Key (ignoring instance): all → {job:api}. LHS has key, RHS has same key → all excluded.
    String q =
        "avg_over_time(cpu_usage[2m]) unless ignoring (instance) avg_over_time(mem_usage[2m])";
    var iv = (InstantVectorResult) eval(ev, q, cm.t2, cm.t2, cm.step);
    assertEquals(0, iv.data().size());
  }
}

/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.okapi.promql.extractor.TimeSeriesExtractor.findValue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.jupiter.api.Test;
import org.okapi.promql.MockSeriesDiscovery;
import org.okapi.promql.MockStatsMerger;
import org.okapi.promql.TestFixtures;
import org.okapi.promql.eval.VectorData.*;
import org.okapi.promql.eval.exceptions.EvaluationException;
import org.okapi.promql.parser.PromQLLexer;
import org.okapi.promql.parser.PromQLParser;

public class OrSetOpTest {

  private static ExpressionResult eval(
      ExpressionEvaluator ev, String q, long start, long end, long step) {
    var parser = new PromQLParser(new CommonTokenStream(new PromQLLexer(CharStreams.fromString(q))));
    return ev.evaluate(q, start, end, step, parser);
  }

  @Test
  void or_lhsPreferenceWhenSameJoinKey() throws EvaluationException {
    var cm = TestFixtures.buildCommonMocks();
    var ev = new ExpressionEvaluator(cm.client, cm.discovery, Executors.newFixedThreadPool(2), new MockStatsMerger());
    // Default matching: join key = all labels except __name__
    // cpu_i1 and mem_i1 share join key {job:api, instance:i1} → LHS (cpu) wins
    // cpu_i2 has unique key → LHS wins. No RHS added.
    var iv = (InstantVectorResult) eval(ev,
        "avg_over_time(cpu_usage[2m]) or avg_over_time(mem_usage[2m])", cm.t2, cm.t2, cm.step);
    assertEquals(2, iv.data().size());
    // LHS cpu values kept
    assertEquals(25f, findValue(iv, new SeriesId("", new Labels(cm.cpuUsageApiI1Tags)), cm.t2), 1e-4);
    assertEquals(70f, findValue(iv, new SeriesId("", new Labels(cm.cpuUsageApiI2Tags)), cm.t2), 1e-4);
  }

  @Test
  void or_addsRhsSeriesWithUncoveredJoinKey() throws EvaluationException {
    var cm = TestFixtures.buildCommonMocks();
    // Add mem_i3 — instance=i3 not in LHS, so or must add it from RHS
    var memI3Tags = Map.of("job", "api", "instance", "i3");
    var memI3 = new SeriesId("mem_usage", new Labels(memI3Tags));
    cm.client.put("mem_usage", memI3Tags, cm.t1, 80f);
    cm.client.put("mem_usage", memI3Tags, cm.t2, 90f);
    var discovery = new MockSeriesDiscovery(
        List.of(cm.cpuUsageApiI1, cm.cpuUsageApiI2, cm.memI1, memI3));
    var ev = new ExpressionEvaluator(cm.client, discovery, Executors.newFixedThreadPool(2), new MockStatsMerger());

    var iv = (InstantVectorResult) eval(ev,
        "avg_over_time(cpu_usage[2m]) or avg_over_time(mem_usage[2m])", cm.t2, cm.t2, cm.step);
    // LHS: cpu_i1(25), cpu_i2(70). RHS: mem_i1 covered by cpu_i1 key; mem_i3 is new → added.
    assertEquals(3, iv.data().size());
    // cpu values
    assertEquals(25f, findValue(iv, new SeriesId("", new Labels(cm.cpuUsageApiI1Tags)), cm.t2), 1e-4);
    assertEquals(70f, findValue(iv, new SeriesId("", new Labels(cm.cpuUsageApiI2Tags)), cm.t2), 1e-4);
    // mem_i3 from RHS: avg(80,90)=85
    assertEquals(85f, findValue(iv, new SeriesId("", new Labels(memI3Tags)), cm.t2), 1e-4);
  }

  @Test
  void or_withOn_respectsOnLabels() throws EvaluationException {
    var cm = TestFixtures.buildCommonMocks();
    var ev = new ExpressionEvaluator(cm.client, cm.discovery, Executors.newFixedThreadPool(2), new MockStatsMerger());
    // on(job): all LHS (cpu_i1, cpu_i2) share key {job:api}. RHS mem_i1 also key {job:api}.
    // LHS covers the key → RHS not added. Result: only LHS.
    var iv = (InstantVectorResult) eval(ev,
        "avg_over_time(cpu_usage[2m]) or on(job) avg_over_time(mem_usage[2m])", cm.t2, cm.t2, cm.step);
    assertEquals(2, iv.data().size());
  }
}

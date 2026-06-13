/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
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

public class RangeVectorBasicsTest {

  private static SeriesWindow findWindow(
      RangeVectorResult rv, String metric, String label, String value) {
    return rv.data().stream()
        .filter(
            w -> metric.equals(w.id().metric()) && value.equals(w.id().labels().tags().get(label)))
        .findFirst()
        .orElseThrow(
            () ->
                new AssertionError(
                    "missing window for " + metric + "{" + label + "=\"" + value + "\"}"));
  }

  private static void assertHasPointAvg(SeriesWindow w, long ts, double expectedAvg) {
    var scan = w.scan();
    if (!(scan instanceof org.okapi.metrics.pojos.results.GaugeScan gs)) {
      throw new AssertionError("expected GaugeScan for series " + w.id());
    }
    var tsList = gs.getTimestamps();
    var valList = gs.getValues();
    int idx = -1;
    for (int i = 0; i < tsList.size(); i++) {
      if (tsList.get(i) == ts) {
        idx = i;
        break;
      }
    }
    if (idx == -1) {
      throw new AssertionError("missing point @ ts=" + ts + " in " + w.id());
    }
    assertEquals(expectedAvg, valList.get(idx), 1e-4, "unexpected avg at ts=" + ts);
  }

  private static Set<String> presentSet(RangeVectorResult rv) {
    var set = new java.util.HashSet<String>();
    for (var w : rv.data()) {
      var inst = w.id().labels().tags().get("instance");
      if (inst != null) {
        set.add(key(w.id().metric(), "instance", inst));
      } else {
        set.add(key(w.id().metric())); // <-- plain metric when no "instance" label
      }
    }
    return set;
  }

  // ---------- helpers ----------

  private static String key(String metric) {
    return metric;
  }

  private static String key(String metric, String k, String v) {
    return metric + "{" + k + "=\"" + v + "\"}";
  }

  @Test
  void cpuUsage_matrixSelector_returnsRangeVector_withExpectedPoints() throws EvaluationException {
    // Arrange shared mocks/data (cpu_usage has two instances with 4 minute buckets t0..t3)
    var cm = TestFixtures.buildCommonMocks();
    var exec = Executors.newFixedThreadPool(2);
    var merger = new MockStatsMerger();
    var evaluator = new ExpressionEvaluator(cm.client, cm.discovery, exec, merger);

    // Evaluate a raw range selector over start..end
    String promql = "cpu_usage[2m]";
    var parser =
        new PromQLParser(new CommonTokenStream(new PromQLLexer(CharStreams.fromString(promql))));

    long start = cm.t1;
    long end = cm.t3;
    long step = cm.step;

    var result = evaluator.evaluate(promql, start, end, step, parser);
    assertEquals(
        ValueType.RANGE_VECTOR, result.type(), "matrix selector should return a range vector");

    var rv = (RangeVectorResult) result;
    assertEquals(2, rv.data().size(), "expected two cpu_usage series (i1 and i2)");

    // i1
    var wI1 = findWindow(rv, "cpu_usage", "instance", "i1");
    assertHasPointAvg(wI1, cm.t0, 10f);
    assertHasPointAvg(wI1, cm.t1, 20f);
    assertHasPointAvg(wI1, cm.t2, 30f);
    assertHasPointAvg(wI1, cm.t3, 40f);

    // i2
    var wI2 = findWindow(rv, "cpu_usage", "instance", "i2");
    assertHasPointAvg(wI2, cm.t0, 40f);
    assertHasPointAvg(wI2, cm.t1, 60f);
    assertHasPointAvg(wI2, cm.t2, 80f);
    assertHasPointAvg(wI2, cm.t3, 100f);
  }

  @Test
  void labelOnlySelector_multipleMetrics_pathsMatch() throws EvaluationException {
    // Matches ALL series with job="api": multiple metrics & instances.
    var cm = TestFixtures.buildCommonMocks();
    var exec = Executors.newFixedThreadPool(2);
    var merger = new MockStatsMerger();
    var evaluator = new ExpressionEvaluator(cm.client, cm.discovery, exec, merger);

    String promql = "{job=\"api\"}[2m]";
    var parser =
        new PromQLParser(new CommonTokenStream(new PromQLLexer(CharStreams.fromString(promql))));

    long start = cm.t2, end = cm.t2, step = cm.step;

    var result = evaluator.evaluate(promql, start, end, step, parser);
    assertEquals(ValueType.RANGE_VECTOR, result.type());

    var rv = (RangeVectorResult) result;

    // Expect at least these known series to be present:
    // - cpu_usage{instance=i1}, cpu_usage{instance=i2}
    // - mem_usage{instance=i1}
    // - pod_replicas{job=api}
    // - http_requests_counter{job=api}
    // - requests{instance=i1}, requests{instance=i2} (added in earlier tests)
    var found = presentSet(rv);

    assertTrue(found.contains(key("cpu_usage", "instance", "i1")), "cpu_usage i1 missing");
    assertTrue(found.contains(key("cpu_usage", "instance", "i2")), "cpu_usage i2 missing");
    assertTrue(found.contains(key("mem_usage", "instance", "i1")), "mem_usage i1 missing");
    assertTrue(found.contains(key("pod_replicas")), "pod_replicas missing");
    assertTrue(found.contains(key("http_requests_counter")), "http_requests_counter missing");

    // Spot-check one point value to ensure data integrity
    var memI1 = findWindow(rv, "mem_usage", "instance", "i1");
    assertHasPointAvg(memI1, cm.t2, 70f); // from fixture: 50,60,70,80 at t0..t3
  }

  @Test
  void regexLabelMatcher_multipleSeriesOfSameMetric() throws EvaluationException {
    // Matches cpu_usage for both instances via regex label matcher.
    var cm = TestFixtures.buildCommonMocks();
    var exec = Executors.newFixedThreadPool(2);
    var merger = new MockStatsMerger();
    var evaluator = new ExpressionEvaluator(cm.client, cm.discovery, exec, merger);

    String promql = "cpu_usage{instance=~\"i.*\"}[2m]";
    var parser =
        new PromQLParser(new CommonTokenStream(new PromQLLexer(CharStreams.fromString(promql))));

    long start = cm.t3, end = cm.t3, step = cm.step;

    var result = evaluator.evaluate(promql, start, end, step, parser);
    assertEquals(ValueType.RANGE_VECTOR, result.type());

    var rv = (RangeVectorResult) result;
    // Should include both i1 and i2
    var found = presentSet(rv);
    assertTrue(found.contains(key("cpu_usage", "instance", "i1")));
    assertTrue(found.contains(key("cpu_usage", "instance", "i2")));
  }
}

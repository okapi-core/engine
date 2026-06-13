/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.jupiter.api.Test;
import org.okapi.promql.MockSeriesDiscovery;
import org.okapi.promql.MockStatsMerger;
import org.okapi.promql.MockTimeSeriesClient;
import org.okapi.promql.eval.VectorData.Labels;
import org.okapi.promql.eval.VectorData.SeriesId;
import org.okapi.promql.eval.exceptions.EvaluationException;
import org.okapi.promql.extractor.TimeSeriesExtractor;
import org.okapi.promql.parser.PromQLLexer;
import org.okapi.promql.parser.PromQLParser;

public class HistogramQuantileTest {
  @Test
  void histogramQuantile_basic() throws EvaluationException {
    var client = new MockTimeSeriesClient();
    long step = 60_000L;
    long t0 = 1_700_000_000_000L;
    long t1 = t0 + step;
    long t2 = t1 + step;
    long t3 = t2 + step;

    var tags = Map.of("job", "api", "instance", "i1");
    var seriesWithInstance = new SeriesId("latency_histo", new Labels(tags));
    var seriesMerged = new SeriesId("latency_histo", new Labels(Map.of("job", "api")));

    // buckets: [100, 200, 500], counts: 10, 20, 30, 40 (total 100)
    // q=0.5 -> target idx ~ 49.5 -> bucket index 2 ([200,500)) -> ~ 400
    client.putHisto(
        "latency_histo", tags, t0, t3, List.of(100f, 200f, 500f), List.of(10, 20, 30, 40));

    var discovery = new MockSeriesDiscovery(List.of(seriesWithInstance));
    var exec = Executors.newFixedThreadPool(2);
    var merger = new MockStatsMerger();
    var evaluator = new ExpressionEvaluator(client, discovery, exec, merger);

    String promql = "histogram_quantile(0.5, latency_histo[2m])";
    var lexer = new PromQLLexer(CharStreams.fromString(promql));
    var tokens = new CommonTokenStream(lexer);
    var parser = new PromQLParser(tokens);

    long start = t1;
    long end = t3;
    var res = evaluator.evaluate(promql, start, end, step, parser);
    var iv = (InstantVectorResult) res;

    double v1 = org.okapi.promql.extractor.TimeSeriesExtractor.findValue(iv, seriesMerged, t1);
    double v2 = org.okapi.promql.extractor.TimeSeriesExtractor.findValue(iv, seriesMerged, t2);
    double v3 = org.okapi.promql.extractor.TimeSeriesExtractor.findValue(iv, seriesMerged, t3);

    assertEquals(400f, v1, 2f); // allow small tolerance due to interpolation choices
    assertEquals(400f, v2, 2f);
    assertEquals(400f, v3, 2f);
  }

  @Test
  void histogramQuantile_edges() throws EvaluationException {
    var client = new MockTimeSeriesClient();
    long step = 60_000L;
    long t0 = 1_700_000_000_000L;
    long t1 = t0 + step;
    long t2 = t1 + step;
    long t3 = t2 + step;

    var tags = Map.of("job", "api");
    var seriesMerged = new SeriesId("latency_histo", new Labels(Map.of("job", "api")));

    client.putHisto(
        "latency_histo", tags, t0, t3, List.of(100f, 200f, 500f), List.of(10, 20, 30, 40));

    var discovery = new MockSeriesDiscovery(List.of(seriesMerged));
    var exec = Executors.newFixedThreadPool(2);
    var merger = new MockStatsMerger();
    var evaluator = new ExpressionEvaluator(client, discovery, exec, merger);

    // q = 0.0 -> expect ~ first finite upper bound (100)
    String q0 = "histogram_quantile(0.0, latency_histo[2m])";
    var res0 =
        evaluator.evaluate(
            q0,
            t1,
            t1,
            step,
            new PromQLParser(new CommonTokenStream(new PromQLLexer(CharStreams.fromString(q0)))));
    double v0 = TimeSeriesExtractor.findValue((InstantVectorResult) res0, seriesMerged, t1);
    assertEquals(100f, v0, 0.001f);

    // q = 1.0 -> expect ~ last finite lower bound (500)
    String q1 = "histogram_quantile(1.0, latency_histo[2m])";
    var res1 =
        evaluator.evaluate(
            q1,
            t1,
            t1,
            step,
            new PromQLParser(new CommonTokenStream(new PromQLLexer(CharStreams.fromString(q1)))));
    double v1 = TimeSeriesExtractor.findValue((InstantVectorResult) res1, seriesMerged, t1);
    assertEquals(500f, v1, 0.001f);
  }
}

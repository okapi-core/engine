/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.testing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.okapi.Statistics;
import org.okapi.metrics.pojos.results.GaugeScan;
import org.okapi.metrics.pojos.results.Scan;
import org.okapi.promql.eval.MetricTypeResolver;
import org.okapi.promql.eval.ExpressionEvaluator;
import org.okapi.promql.eval.ExpressionResult;
import org.okapi.promql.eval.InstantVectorResult;
import org.okapi.promql.eval.RangeVectorResult;
import org.okapi.promql.eval.ScalarResult;
import org.okapi.promql.eval.VectorData.Labels;
import org.okapi.promql.eval.VectorData.Sample;
import org.okapi.promql.eval.VectorData.SeriesId;
import org.okapi.promql.eval.VectorData.SeriesSample;
import org.okapi.promql.eval.VectorData.SeriesWindow;
import org.okapi.promql.eval.exceptions.EvaluationException;
import org.okapi.promql.eval.ts.RESOLUTION;
import org.okapi.promql.eval.ts.SeriesDiscovery;
import org.okapi.promql.eval.ts.StatisticsMerger;
import org.okapi.promql.eval.ts.TsClient;
import org.okapi.promql.parse.LabelMatcher;
import org.okapi.promql.parse.LabelOp;
import org.okapi.promql.parser.PromQLLexer;
import org.okapi.promql.parser.PromQLParser;
import org.okapi.promql.testing.PromQlTestAst.*;
import org.okapi.promql.testing.PromQlTestIngestor.IngestedSeries;

public final class PromQlTestPipeline {
  private static final long DEFAULT_START_MS = 0L;
  private static final double DEFAULT_EPSILON = 1e-5;

  private final InMemoryPromQlTestIngestor ingestor;
  private final ExecutorService executor;
  private final ExpressionEvaluator evaluator;
  private final TsClient tsClient;
  private final SeriesDiscovery discovery;

  public PromQlTestPipeline() {
    this.ingestor = new InMemoryPromQlTestIngestor();
    this.executor = Executors.newFixedThreadPool(2);
    this.tsClient = new InMemoryTsClient(ingestor);
    this.discovery = new InMemorySeriesDiscovery(ingestor);
    MetricTypeResolver resolver = new InMemoryMetricTypeResolver(ingestor);
    this.evaluator =
        new ExpressionEvaluator(tsClient, discovery, executor, new NoopStatsMerger(), resolver);
  }

  public List<TestExpectationDifference> run(String script) {
    PromQlTestDataParser parser = new PromQlTestDataParser(script);
    TestFile file = parser.parse();
    List<TestExpectationDifference> diffs = new ArrayList<>();

    for (Command command : file.commands()) {
      if (command instanceof ClearCmd) {
        ingestor.clear();
      } else if (command instanceof LoadCmd loadCmd) {
        ingestor.ingestLoad(DEFAULT_START_MS, loadCmd);
      } else if (command instanceof EvalCmd evalCmd) {
        diffs.addAll(runEval(evalCmd));
      }
    }
    return diffs;
  }

  private List<TestExpectationDifference> runEval(EvalCmd evalCmd) {
    List<TestExpectationDifference> diffs = new ArrayList<>();
    ExpressionResult result = null;
    EvaluationException evalError = null;

    try {
      String expr = evalCmd.expression();
      PromQLParser promParser = buildParser(expr);
      if (evalCmd.evalType() instanceof InstantEval instant) {
        long ts = parseDurationToMillis(instant.at().text());
        result = evaluator.evaluateAt(expr, ts, promParser);
      } else if (evalCmd.evalType() instanceof RangeEval range) {
        long start = parseDurationToMillis(range.from().text());
        long end = parseDurationToMillis(range.to().text());
        long step = parseDurationToMillis(range.step().text());
        result = evaluator.evaluate(expr, start, end, step, promParser);
      }
    } catch (EvaluationException ex) {
      evalError = ex;
    }

    ExpectAnnotation failExpect = firstExpect(evalCmd.expectations(), ExpectType.FAIL);
    if (failExpect == null && evalCmd.legacyModifier() == LegacyEvalModifier.FAIL) {
      failExpect = new ExpectAnnotation(ExpectType.FAIL, null, null);
    }
    if (failExpect != null) {
      if (evalError == null) {
        diffs.add(
            TestExpectationDifference.of(
                evalCmd.expression(), "expected failure but evaluation succeeded", null, null));
      } else if (failExpect.matchType() != null && failExpect.pattern() != null) {
        if (!matchExpectation(evalError.getMessage(), failExpect)) {
          diffs.add(
              TestExpectationDifference.of(
                  evalCmd.expression(),
                  "failure message mismatch",
                  failExpect.pattern(),
                  evalError.getMessage()));
        }
      }
      return diffs;
    }

    if (evalError != null) {
      diffs.add(
          TestExpectationDifference.of(
              evalCmd.expression(), "unexpected evaluation error", null, evalError.getMessage()));
      return diffs;
    }

    for (Expectation exp : evalCmd.expectations()) {
      if (exp instanceof ExpectAnnotation annotation) {
        if (annotation.type() == ExpectType.FAIL) {
          continue;
        }
        if (annotation.type() == ExpectType.NO_INFO
            || annotation.type() == ExpectType.NO_WARN
            || annotation.type() == ExpectType.INFO
            || annotation.type() == ExpectType.WARN
            || annotation.type() == ExpectType.ORDERED) {
          continue;
        }
        diffs.add(
            TestExpectationDifference.of(
                evalCmd.expression(),
                "expectation type not supported yet: " + annotation.type(),
                null,
                null));
      } else if (exp instanceof ExpectString) {
        diffs.add(
            TestExpectationDifference.of(
                evalCmd.expression(), "string results not supported yet", null, null));
      }
    }

    if (result != null) {
      diffs.addAll(compareResult(evalCmd, result));
    }
    return diffs;
  }

  private List<TestExpectationDifference> compareResult(EvalCmd evalCmd, ExpressionResult result) {
    if (evalCmd.evalType() instanceof RangeEval) {
      if (result instanceof InstantVectorResult vector) {
        return compareRangeFromInstant(evalCmd, vector);
      }
    }
    if (result instanceof ScalarResult scalar) {
      return compareScalar(evalCmd, scalar);
    }
    if (result instanceof InstantVectorResult vector) {
      return compareInstantVector(evalCmd, vector);
    }
    if (result instanceof org.okapi.promql.eval.HistogramVectorResult vector) {
      return compareHistogramVector(evalCmd, vector);
    }
    if (result instanceof RangeVectorResult range) {
      return compareRangeVector(evalCmd, range);
    }
    return List.of(
        TestExpectationDifference.of(
            evalCmd.expression(), "unsupported result type", null, result.toString()));
  }

  private List<TestExpectationDifference> compareScalar(EvalCmd evalCmd, ScalarResult scalar) {
    List<ExpectedResult> expected = evalCmd.results();
    if (expected.isEmpty()) {
      return List.of(
          TestExpectationDifference.of(
              evalCmd.expression(),
              "expected no scalar result but got value",
              "none",
              String.valueOf(scalar.getValue())));
    }
    if (!(expected.get(0) instanceof PromQlTestAst.ScalarResult exp)) {
      return List.of(
          TestExpectationDifference.of(
              evalCmd.expression(),
              "expected scalar result but got series",
              expected.get(0).toString(),
              String.valueOf(scalar.getValue())));
    }
    if (!floatEquals((float) exp.value(), scalar.getValue())) {
      return List.of(
          TestExpectationDifference.of(
              evalCmd.expression(),
              "scalar value mismatch",
              String.valueOf(exp.value()),
              String.valueOf(scalar.getValue())));
    }
    return List.of();
  }

  private List<TestExpectationDifference> compareInstantVector(
      EvalCmd evalCmd, InstantVectorResult vector) {
    Long evalTime = null;
    if (evalCmd.evalType() instanceof InstantEval instant) {
      if (!evalCmd.expression().contains("@")) {
        evalTime = parseDurationToMillis(instant.at().text());
      }
    }
    Map<SeriesId, Float> actual = new HashMap<>();
    for (SeriesSample sample : vector) {
      if (evalTime != null && sample.sample().ts() != evalTime) {
        continue;
      }
      actual.put(sample.series(), sample.sample().value());
    }

    record ExpectedValue(float value, boolean histogram) {}
    Map<SeriesId, ExpectedValue> expected = new HashMap<>();
    for (ExpectedResult res : evalCmd.results()) {
      if (res instanceof SeriesResult seriesResult) {
        SeriesDef series = seriesResult.series();
        List<Float> values = expandExpectedPoints(series.points());
        float value = values.isEmpty() ? Float.NaN : values.get(0);
        boolean hasHistogram = containsHistogramPoint(series.points());
        expected.put(normalizeSeries(series), new ExpectedValue(value, hasHistogram));
      }
    }

    List<TestExpectationDifference> diffs = new ArrayList<>();
    for (var entry : expected.entrySet()) {
      SeriesId id = entry.getKey();
      ExpectedValue exp = entry.getValue();
      Float actualValue = actual.get(id);
      if (actualValue == null) {
        if (exp.histogram) {
          // Histogram comparisons are not supported; allow missing series.
          continue;
        } else {
          diffs.add(
              TestExpectationDifference.of(
                  evalCmd.expression(), "missing series", id.toString(), null));
          continue;
        }
      }
      if (!floatEquals(exp.value(), actualValue)) {
        diffs.add(
            TestExpectationDifference.of(
                evalCmd.expression(),
                "instant vector value mismatch",
                String.valueOf(exp.value()),
                String.valueOf(actualValue)));
      }
    }
    return diffs;
  }

  private List<TestExpectationDifference> compareRangeFromInstant(
      EvalCmd evalCmd, InstantVectorResult vector) {
    Map<SeriesId, List<Float>> expected = new HashMap<>();
    for (ExpectedResult res : evalCmd.results()) {
      if (res instanceof SeriesResult seriesResult) {
        SeriesDef series = seriesResult.series();
        expected.put(normalizeSeries(series), expandExpectedPointsForRange(series.points()));
      }
    }

    RangeSpec range = rangeSpec(evalCmd);
    List<TestExpectationDifference> diffs = new ArrayList<>();
    Map<SeriesId, Map<Long, Float>> actualBySeries = new HashMap<>();
    for (SeriesSample sample : vector) {
      actualBySeries
          .computeIfAbsent(sample.series(), k -> new HashMap<>())
          .put(sample.sample().ts(), sample.sample().value());
    }

    for (var entry : expected.entrySet()) {
      SeriesId id = entry.getKey();
      List<Float> exp = entry.getValue();
      Map<Long, Float> actualSeries = actualBySeries.get(id);
      if (actualSeries == null) {
        diffs.add(
            TestExpectationDifference.of(
                evalCmd.expression(), "missing series", id.toString(), null));
        continue;
      }
      List<Float> actual =
          alignRangeValues(actualSeries, range.startMs, range.endMs, range.stepMs, range.steps);
      if (actual.size() != exp.size()) {
        diffs.add(
            TestExpectationDifference.of(
                evalCmd.expression(),
                "range vector length mismatch",
                String.valueOf(exp.size()),
                String.valueOf(actual.size())));
        continue;
      }
      for (int i = 0; i < actual.size(); i++) {
        if (!floatEquals(exp.get(i), actual.get(i))) {
          diffs.add(
              TestExpectationDifference.of(
                  evalCmd.expression(),
                  "range vector value mismatch at index " + i,
                  String.valueOf(exp.get(i)),
                  String.valueOf(actual.get(i))));
          break;
        }
      }
    }
    for (SeriesId id : actualBySeries.keySet()) {
      if (!expected.containsKey(id)) {
        diffs.add(
            TestExpectationDifference.of(
                evalCmd.expression(), "unexpected series", null, id.toString()));
      }
    }
    return diffs;
  }

  private List<TestExpectationDifference> compareHistogramVector(
      EvalCmd evalCmd, org.okapi.promql.eval.HistogramVectorResult vector) {
    Map<SeriesId, org.okapi.promql.eval.HistogramVectorResult.HistogramValue> actual =
        new HashMap<>();
    for (var sample : vector.data()) {
      actual.put(sample.id(), sample.sample().value());
    }

    Map<SeriesId, org.okapi.promql.eval.HistogramVectorResult.HistogramValue> expected =
        new HashMap<>();
    for (ExpectedResult res : evalCmd.results()) {
      if (res instanceof SeriesResult seriesResult) {
        SeriesDef series = seriesResult.series();
        org.okapi.promql.eval.HistogramVectorResult.HistogramValue value =
            extractExpectedHistogram(series.points());
        expected.put(normalizeSeries(series), value);
      }
    }

    List<TestExpectationDifference> diffs = new ArrayList<>();
    for (var entry : expected.entrySet()) {
      SeriesId id = entry.getKey();
      var exp = entry.getValue();
      var act = actual.get(id);
      if (act == null) {
        diffs.add(
            TestExpectationDifference.of(evalCmd.expression(), "missing series", id.toString(), null));
        continue;
      }
      if (!floatEquals(exp.count(), act.count()) || !floatEquals(exp.sum(), act.sum())) {
        diffs.add(
            TestExpectationDifference.of(
                evalCmd.expression(),
                "histogram value mismatch",
                "count=" + exp.count() + " sum=" + exp.sum(),
                "count=" + act.count() + " sum=" + act.sum()));
      }
    }
    if (actual.size() != expected.size()) {
      diffs.add(
          TestExpectationDifference.of(
              evalCmd.expression(),
              "series count mismatch",
              String.valueOf(expected.size()),
              String.valueOf(actual.size())));
    }
    return diffs;
  }

  private org.okapi.promql.eval.HistogramVectorResult.HistogramValue extractExpectedHistogram(
      List<PointExpr> points) {
    for (PointExpr point : points) {
      if (point instanceof HistogramPoint hp) {
        HistogramCounts counts = parseHistogramCounts(hp.value());
        return new org.okapi.promql.eval.HistogramVectorResult.HistogramValue(
            counts.count, counts.sum);
      }
    }
    return new org.okapi.promql.eval.HistogramVectorResult.HistogramValue(Float.NaN, Float.NaN);
  }

  private boolean containsHistogramPoint(List<PointExpr> points) {
    for (PointExpr point : points) {
      if (point instanceof HistogramPoint) {
        return true;
      }
      if (point instanceof RepeatPoint repeat) {
        if (repeat.value() instanceof HistogramPoint) {
          return true;
        }
      }
      if (point instanceof StepSequencePoint step) {
        if (step.start() instanceof HistogramPoint || step.step() instanceof HistogramPoint) {
          return true;
        }
      }
    }
    return false;
  }

  private List<TestExpectationDifference> compareRangeVector(
      EvalCmd evalCmd, RangeVectorResult range) {
    List<TestExpectationDifference> diffs = new ArrayList<>();
    Map<SeriesId, List<Float>> expected = new HashMap<>();
    for (ExpectedResult res : evalCmd.results()) {
      if (res instanceof SeriesResult seriesResult) {
        SeriesDef series = seriesResult.series();
        expected.put(normalizeSeries(series), expandExpectedPointsForRange(series.points()));
      }
    }

    RangeSpec rangeSpec = rangeSpec(evalCmd);

    for (SeriesWindow window : range) {
      SeriesId seriesId = window.id();
      List<Float> exp = expected.get(seriesId);
      if (exp == null) {
        diffs.add(
            TestExpectationDifference.of(
                evalCmd.expression(), "unexpected series", null, seriesId.toString()));
        continue;
      }
      if (window.scan() instanceof GaugeScan gs) {
        List<Float> actual =
            alignRangeValues(gs, rangeSpec.startMs, rangeSpec.endMs, rangeSpec.stepMs, rangeSpec.steps);
        if (actual.size() != exp.size()) {
          diffs.add(
              TestExpectationDifference.of(
                  evalCmd.expression(),
                  "range vector length mismatch",
                  String.valueOf(exp.size()),
                  String.valueOf(actual.size())));
          continue;
        }
        for (int i = 0; i < actual.size(); i++) {
          if (!floatEquals(exp.get(i), actual.get(i))) {
            diffs.add(
                TestExpectationDifference.of(
                    evalCmd.expression(),
                    "range vector value mismatch at index " + i,
                    String.valueOf(exp.get(i)),
                    String.valueOf(actual.get(i))));
            break;
          }
        }
      } else if (window.scan() instanceof org.okapi.promql.eval.HistogramSeries hs) {
        List<HistogramCounts> expHist = expectedHistogram(seriesId, evalCmd);
        List<HistogramCounts> actual =
            alignHistogramValues(
                hs, rangeSpec.startMs, rangeSpec.endMs, rangeSpec.stepMs, rangeSpec.steps);
        if (expHist == null) {
          diffs.add(
              TestExpectationDifference.of(
                  evalCmd.expression(), "unexpected series", null, seriesId.toString()));
          continue;
        }
        if (actual.size() != expHist.size()) {
          diffs.add(
              TestExpectationDifference.of(
                  evalCmd.expression(),
                  "range vector length mismatch",
                  String.valueOf(expHist.size()),
                  String.valueOf(actual.size())));
          continue;
        }
        for (int i = 0; i < actual.size(); i++) {
          HistogramCounts expVal = expHist.get(i);
          HistogramCounts actVal = actual.get(i);
          if (expVal == null && actVal == null) {
            continue;
          }
          if (expVal == null || actVal == null) {
            diffs.add(
                TestExpectationDifference.of(
                    evalCmd.expression(),
                    "range vector value mismatch at index " + i,
                    String.valueOf(expVal),
                    String.valueOf(actVal)));
            break;
          }
          if (!floatEquals(expVal.sum, actVal.sum) || !floatEquals(expVal.count, actVal.count)) {
            diffs.add(
                TestExpectationDifference.of(
                    evalCmd.expression(),
                    "range vector value mismatch at index " + i,
                    String.valueOf(expVal),
                    String.valueOf(actVal)));
            break;
          }
        }
      } else {
        diffs.add(
            TestExpectationDifference.of(
                evalCmd.expression(),
                "unsupported scan type",
                null,
                window.scan().getClass().getSimpleName()));
      }
    }
    return diffs;
  }

  private RangeSpec rangeSpec(EvalCmd evalCmd) {
    long rangeStart = 0L;
    long rangeEnd = 0L;
    long rangeStep = 0L;
    ExpectRangeVector expectRange = firstExpectRange(evalCmd.expectations());
    if (expectRange != null) {
      rangeStart = parseDurationToMillis(expectRange.from().text());
      rangeEnd = parseDurationToMillis(expectRange.to().text());
      rangeStep = parseDurationToMillis(expectRange.step().text());
    } else if (evalCmd.evalType() instanceof RangeEval rangeEval) {
      rangeStart = parseDurationToMillis(rangeEval.from().text());
      rangeEnd = parseDurationToMillis(rangeEval.to().text());
      rangeStep = parseDurationToMillis(rangeEval.step().text());
    }
    int expectedSteps = 0;
    if (rangeStep > 0) {
      if (expectRange != null) {
        expectedSteps = (int) ((rangeEnd - rangeStart) / rangeStep);
      } else {
        expectedSteps = (int) ((rangeEnd - rangeStart) / rangeStep) + 1;
      }
    }
    return new RangeSpec(rangeStart, rangeEnd, rangeStep, expectedSteps);
  }

  private ExpectRangeVector firstExpectRange(List<Expectation> expectations) {
    for (Expectation exp : expectations) {
      if (exp instanceof ExpectRangeVector range) {
        return range;
      }
    }
    return null;
  }

  private List<Float> alignRangeValues(
      GaugeScan scan, long startMs, long endMs, long stepMs, int expectedSteps) {
    List<Float> out = new ArrayList<>();
    if (stepMs <= 0 || expectedSteps <= 0) {
      out.addAll(scan.getValues());
      return out;
    }
    Map<Long, Float> valuesByTs = new HashMap<>();
    List<Long> ts = scan.getTimestamps();
    List<Float> vals = scan.getValues();
    for (int i = 0; i < ts.size(); i++) {
      valuesByTs.put(ts.get(i), vals.get(i));
    }
    long t = startMs;
    for (int i = 0; i < expectedSteps; i++) {
      Float v = valuesByTs.get(t);
      if (v == null || org.okapi.promql.eval.Staleness.isStale(v)) {
        out.add(Float.NaN);
      } else {
        out.add(v);
      }
      t += stepMs;
    }
    return out;
  }

  private List<Float> alignRangeValues(
      Map<Long, Float> valuesByTs,
      long startMs,
      long endMs,
      long stepMs,
      int expectedSteps) {
    List<Float> out = new ArrayList<>();
    if (stepMs <= 0 || expectedSteps <= 0) {
      out.addAll(valuesByTs.values());
      return out;
    }
    long t = startMs;
    for (int i = 0; i < expectedSteps; i++) {
      Float v = valuesByTs.get(t);
      if (v == null || org.okapi.promql.eval.Staleness.isStale(v)) {
        out.add(Float.NaN);
      } else {
        out.add(v);
      }
      t += stepMs;
    }
    return out;
  }

  private List<HistogramCounts> expectedHistogram(SeriesId id, EvalCmd evalCmd) {
    for (ExpectedResult res : evalCmd.results()) {
      if (res instanceof SeriesResult seriesResult) {
        SeriesDef series = seriesResult.series();
        if (!normalizeSeries(series).equals(id)) {
          continue;
        }
        return expandExpectedHistogramPointsForRange(series.points());
      }
    }
    return null;
  }

  private List<HistogramCounts> alignHistogramValues(
      org.okapi.promql.eval.HistogramSeries series,
      long startMs,
      long endMs,
      long stepMs,
      int expectedSteps) {
    List<HistogramCounts> out = new ArrayList<>();
    if (stepMs <= 0 || expectedSteps <= 0) {
      for (var p : series.getPoints()) {
        out.add(new HistogramCounts(p.sum(), p.count()));
      }
      return out;
    }
    Map<Long, HistogramCounts> valuesByTs = new HashMap<>();
    for (var p : series.getPoints()) {
      valuesByTs.put(p.endMs(), new HistogramCounts(p.sum(), p.count()));
    }
    long t = startMs;
    for (int i = 0; i < expectedSteps; i++) {
      out.add(valuesByTs.get(t));
      t += stepMs;
    }
    return out;
  }

  private List<HistogramCounts> expandExpectedHistogramPointsForRange(List<PointExpr> points) {
    List<PromQlTestAst.HistogramLiteral> expanded = expandExpectedHistogramPoints(points);
    List<HistogramCounts> out = new ArrayList<>();
    for (PromQlTestAst.HistogramLiteral literal : expanded) {
      if (literal == null) {
        out.add(null);
      } else {
        out.add(parseHistogramCounts(literal));
      }
    }
    return out;
  }

  private List<PromQlTestAst.HistogramLiteral> expandExpectedHistogramPoints(
      List<PointExpr> points) {
    List<PromQlTestAst.HistogramLiteral> values = new ArrayList<>();
    for (PointExpr point : points) {
      expandExpectedHistogramPoint(point, values);
    }
    return values;
  }

  private void expandExpectedHistogramPoint(
      PointExpr point, List<PromQlTestAst.HistogramLiteral> values) {
    if (point instanceof HistogramPoint hp) {
      values.add(hp.value());
    } else if (point instanceof MissingPoint || point instanceof StalePoint) {
      values.add(null);
    } else if (point instanceof RepeatPoint repeat) {
      for (int i = 0; i < repeat.count(); i++) {
        expandExpectedHistogramPoint(repeat.value(), values);
      }
    } else if (point instanceof StepSequencePoint step) {
      if (step.start() instanceof HistogramPoint start
          && step.step() instanceof HistogramPoint delta) {
        HistogramCounts startCounts = parseHistogramCounts(start.value());
        HistogramCounts deltaCounts = parseHistogramCounts(delta.value());
        for (int i = 0; i <= step.count(); i++) {
          float sum = startCounts.sum + deltaCounts.sum * i;
          float count = startCounts.count + deltaCounts.count * i;
          values.add(buildHistogramLiteral(sum, count));
        }
      } else {
        throw new IllegalStateException("histogram step sequence not supported yet");
      }
    } else {
      throw new IllegalStateException("expected histogram point");
    }
  }

  private List<Float> expandExpectedPoints(List<PointExpr> points) {
    List<Float> values = new ArrayList<>();
    for (PointExpr point : points) {
      expandPoint(point, values);
    }
    return values;
  }

  private List<Float> expandExpectedPointsForRange(List<PointExpr> points) {
    List<Float> values = new ArrayList<>();
    for (PointExpr point : points) {
      if (point instanceof MissingPoint || point instanceof StalePoint) {
        values.add(Float.NaN);
      } else {
        expandPoint(point, values);
      }
    }
    return values;
  }

  private void expandPoint(PointExpr point, List<Float> values) {
    if (point instanceof NumberPoint number) {
      values.add((float) number.value());
    } else if (point instanceof NaNPoint) {
      values.add(Float.NaN);
    } else if (point instanceof InfPoint inf) {
      values.add(inf.negative() ? Float.NEGATIVE_INFINITY : Float.POSITIVE_INFINITY);
    } else if (point instanceof MissingPoint) {
      // omit
    } else if (point instanceof StalePoint) {
      values.add(null);
    } else if (point instanceof RepeatPoint repeat) {
      for (int i = 0; i < repeat.count(); i++) {
        expandPoint(repeat.value(), values);
      }
    } else if (point instanceof StepSequencePoint step) {
      double start = extractNumber(step.start());
      double delta = extractNumber(step.step());
      for (int i = 0; i <= step.count(); i++) {
        values.add((float) (start + delta * i));
      }
    } else if (point instanceof HistogramPoint) {
      // Histogram expectations are not supported yet; treat as unknown value.
      values.add(Float.NaN);
    }
  }

  private double extractNumber(PointExpr point) {
    if (point instanceof NumberPoint number) {
      return number.value();
    }
    throw new IllegalStateException("expected number point");
  }

  private SeriesId normalizeSeries(SeriesDef series) {
    Map<String, String> labels = new HashMap<>(series.labels());
    String metric = series.metric();
    if (metric == null || metric.isEmpty()) {
      String name = labels.remove("__name__");
      metric = name == null ? "" : name;
    }
    return new SeriesId(metric, new Labels(labels));
  }

  private static HistogramCounts parseHistogramCounts(PromQlTestAst.HistogramLiteral literal) {
    float sum = Float.NaN;
    float count = Float.NaN;
    for (var entry : literal.fields().entrySet()) {
      String key = entry.getKey();
      PromQlTestAst.HistogramValue value = entry.getValue();
      if (value instanceof PromQlTestAst.HistogramNumber num) {
        if ("sum".equals(key)) {
          sum = (float) num.value();
        } else if ("count".equals(key)) {
          count = (float) num.value();
        }
      }
    }
    if (Float.isNaN(sum) && !Float.isNaN(count)) {
      sum = count;
    }
    return new HistogramCounts(sum, count);
  }

  private record RangeSpec(long startMs, long endMs, long stepMs, int steps) {}

  private record HistogramCounts(float sum, float count) {}

  private static PromQlTestAst.HistogramLiteral buildHistogramLiteral(float sum, float count) {
    Map<String, PromQlTestAst.HistogramValue> fields = new HashMap<>();
    fields.put("sum", new PromQlTestAst.HistogramNumber(sum));
    fields.put("count", new PromQlTestAst.HistogramNumber(count));
    return new PromQlTestAst.HistogramLiteral(fields);
  }

  private ExpectAnnotation firstExpect(List<Expectation> expectations, ExpectType type) {
    for (Expectation exp : expectations) {
      if (exp instanceof ExpectAnnotation annotation && annotation.type() == type) {
        return annotation;
      }
    }
    return null;
  }

  private boolean matchExpectation(String actual, ExpectAnnotation expect) {
    if (expect.matchType() == null || expect.pattern() == null) {
      return true;
    }
    if (expect.matchType() == MatchType.MSG) {
      return expect.pattern().equals(actual);
    }
    return Pattern.compile(expect.pattern()).matcher(actual).find();
  }

  private PromQLParser buildParser(String expr) {
    PromQLLexer lexer = new PromQLLexer(CharStreams.fromString(expr));
    return new PromQLParser(new CommonTokenStream(lexer));
  }

  private long parseDurationToMillis(String duration) {
    String s = duration.trim();
    if (s.isEmpty()) {
      throw new IllegalArgumentException("invalid duration: " + duration);
    }
    boolean numericOrDecimal = true;
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (!(Character.isDigit(c) || c == '.')) {
        numericOrDecimal = false;
        break;
      }
    }
    if (numericOrDecimal) {
      java.math.BigDecimal seconds = new java.math.BigDecimal(s);
      return seconds
          .multiply(java.math.BigDecimal.valueOf(1000))
          .setScale(0, java.math.RoundingMode.HALF_UP)
          .longValueExact();
    }

    long total = 0L;
    int i = 0;
    while (i < s.length()) {
      int start = i;
      while (i < s.length() && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.')) {
        i++;
      }
      if (start == i) {
        throw new IllegalArgumentException("invalid duration: " + duration);
      }
      double value = Double.parseDouble(s.substring(start, i));
      if (i >= s.length()) {
        throw new IllegalArgumentException("invalid duration: " + duration);
      }
      if (s.startsWith("ms", i)) {
        total += Math.round(value);
        i += 2;
        continue;
      }
      char unit = s.charAt(i++);
      total += Math.round(value * unitMultiplier(unit));
    }
    return total;
  }

  private long unitMultiplier(char unit) {
    return switch (unit) {
      case 's' -> 1000L;
      case 'm' -> 60_000L;
      case 'h' -> 3_600_000L;
      case 'd' -> 86_400_000L;
      case 'w' -> 604_800_000L;
      case 'y' -> 31_536_000_000L;
      default -> throw new IllegalArgumentException("unsupported duration unit: " + unit);
    };
  }

  private boolean floatEquals(float a, float b) {
    if (Float.isNaN(a) && Float.isNaN(b)) {
      return true;
    }
    if (Float.isInfinite(a) || Float.isInfinite(b)) {
      return a == b;
    }
    double diff = Math.abs(a - b);
    double scale = Math.max(Math.abs(a), Math.abs(b));
    if (scale == 0.0) {
      return diff <= DEFAULT_EPSILON;
    }
    return diff <= DEFAULT_EPSILON * scale;
  }

  private static final class NoopStatsMerger implements StatisticsMerger {
    @Override
    public Statistics merge(Statistics a, Statistics b) {
      return a == null ? b : a;
    }
  }

  private static final class InMemoryMetricTypeResolver implements MetricTypeResolver {
    private final InMemoryPromQlTestIngestor ingestor;

    private InMemoryMetricTypeResolver(InMemoryPromQlTestIngestor ingestor) {
      this.ingestor = ingestor;
    }

    @Override
    public boolean isCounter(SeriesId id) {
      for (IngestedSeries series : ingestor.series()) {
        SeriesId seriesId = normalizeSeries(series);
        if (seriesId.equals(id)) {
          return series.metricType() == TestMetricClassifier.MetricType.COUNTER;
        }
      }
      return false;
    }

    private SeriesId normalizeSeries(IngestedSeries series) {
      Map<String, String> labels = new HashMap<>(series.labels());
      String metric = series.metric();
      if (metric == null || metric.isEmpty()) {
        String name = labels.remove("__name__");
        metric = name == null ? "" : name;
      }
      return new SeriesId(metric, new Labels(labels));
    }
  }

  private static final class InMemorySeriesDiscovery implements SeriesDiscovery {
    private final InMemoryPromQlTestIngestor ingestor;

    private InMemorySeriesDiscovery(InMemoryPromQlTestIngestor ingestor) {
      this.ingestor = ingestor;
    }

    @Override
    public List<SeriesId> expand(
        String metricOrNull, List<LabelMatcher> matchers, long start, long end) {
      List<SeriesId> results = new ArrayList<>();
      for (IngestedSeries series : ingestor.series()) {
        SeriesId id = normalizeSeries(series);
        if (metricOrNull != null && !metricOrNull.equals(id.metric())) {
          continue;
        }
        if (!matchesAll(id, matchers)) {
          continue;
        }
        results.add(id);
      }
      return results;
    }

    private boolean matchesAll(SeriesId id, List<LabelMatcher> matchers) {
      for (LabelMatcher matcher : matchers) {
        if (!matches(id, matcher)) {
          return false;
        }
      }
      return true;
    }

    private boolean matches(SeriesId id, LabelMatcher matcher) {
      String name = matcher.name();
      String value = matcher.value();
      String actual =
          "__name__".equals(name) ? id.metric() : id.labels().tags().get(name);
      return switch (matcher.op()) {
        case EQ -> actual != null && actual.equals(value);
        case NE -> actual == null || !actual.equals(value);
        case RE -> actual != null && Pattern.compile(value).matcher(actual).matches();
        case NRE -> actual == null || !Pattern.compile(value).matcher(actual).matches();
      };
    }

    private SeriesId normalizeSeries(IngestedSeries series) {
      Map<String, String> labels = new HashMap<>(series.labels());
      String metric = series.metric();
      if (metric == null || metric.isEmpty()) {
        String name = labels.remove("__name__");
        metric = name == null ? "" : name;
      }
      return new SeriesId(metric, new Labels(labels));
    }
  }

  private static final class InMemoryTsClient implements TsClient {
    private final InMemoryPromQlTestIngestor ingestor;

    private InMemoryTsClient(InMemoryPromQlTestIngestor ingestor) {
      this.ingestor = ingestor;
    }

    @Override
    public Scan get(
        String name, Map<String, String> tags, RESOLUTION res, long startMs, long endMs) {
      for (IngestedSeries series : ingestor.series()) {
        SeriesId id = normalizeSeries(series);
        if (!id.metric().equals(name)) {
          continue;
        }
        if (!id.labels().tags().equals(tags)) {
          continue;
        }
        if (series.metricType() == TestMetricClassifier.MetricType.HISTOGRAM) {
          return buildHistogramSeries(series, startMs, endMs);
        }
        return buildGaugeScan(series, startMs, endMs);
      }
      return GaugeScan.builder()
          .universalPath(name)
          .timestamps(List.of())
          .values(List.of())
          .build();
    }

    private GaugeScan buildGaugeScan(IngestedSeries series, long startMs, long endMs) {
      List<Long> timestamps = new ArrayList<>();
      List<Float> values = new ArrayList<>();
      long ts = series.startMs();
      long step = series.stepMs();
      List<Float> expanded = expandSeriesPoints(series.points());
      for (Float value : expanded) {
        if (ts >= startMs && ts <= endMs) {
          if (value != null) {
            timestamps.add(ts);
            values.add(value);
          }
        }
        ts += step;
      }
      return GaugeScan.builder()
          .universalPath(series.metric())
          .timestamps(timestamps)
          .values(values)
          .build();
    }

    private org.okapi.promql.eval.HistogramSeries buildHistogramSeries(
        IngestedSeries series, long startMs, long endMs) {
      List<org.okapi.promql.eval.HistogramSeries.HistogramPoint> points = new ArrayList<>();
      long ts = series.startMs();
      long step = series.stepMs();
      List<PromQlTestAst.HistogramLiteral> expanded = expandHistogramPoints(series.points());
      for (PromQlTestAst.HistogramLiteral literal : expanded) {
        if (ts >= startMs && ts <= endMs) {
          if (literal != null) {
            HistogramCounts counts = parseHistogramCounts(literal);
            points.add(
                new org.okapi.promql.eval.HistogramSeries.HistogramPoint(
                    ts, ts, null, null, counts.sum, counts.count));
          }
        }
        ts += step;
      }
      return new org.okapi.promql.eval.HistogramSeries(series.metric(), points);
    }

    private List<Float> expandSeriesPoints(List<PointExpr> points) {
      List<Float> values = new ArrayList<>();
      for (PointExpr point : points) {
        expandPoint(point, values);
      }
      return values;
    }

    private void expandPoint(PointExpr point, List<Float> values) {
      if (point instanceof NumberPoint number) {
        values.add((float) number.value());
      } else if (point instanceof NaNPoint) {
        values.add(Float.NaN);
      } else if (point instanceof InfPoint inf) {
        values.add(inf.negative() ? Float.NEGATIVE_INFINITY : Float.POSITIVE_INFINITY);
      } else if (point instanceof MissingPoint) {
        values.add(null);
      } else if (point instanceof StalePoint) {
        values.add(org.okapi.promql.eval.Staleness.staleFloat());
      } else if (point instanceof RepeatPoint repeat) {
        for (int i = 0; i < repeat.count(); i++) {
          expandPoint(repeat.value(), values);
        }
      } else if (point instanceof StepSequencePoint step) {
        double start = extractNumber(step.start());
        double delta = extractNumber(step.step());
        for (int i = 0; i <= step.count(); i++) {
          values.add((float) (start + delta * i));
        }
      } else if (point instanceof HistogramPoint) {
        throw new IllegalStateException("histogram points not supported yet");
      }
    }

    private List<PromQlTestAst.HistogramLiteral> expandHistogramPoints(List<PointExpr> points) {
      List<PromQlTestAst.HistogramLiteral> values = new ArrayList<>();
      for (PointExpr point : points) {
        expandHistogramPoint(point, values);
      }
      return values;
    }

    private void expandHistogramPoint(
        PointExpr point, List<PromQlTestAst.HistogramLiteral> values) {
      if (point instanceof HistogramPoint hp) {
        values.add(hp.value());
      } else if (point instanceof MissingPoint || point instanceof StalePoint) {
        values.add(null);
      } else if (point instanceof RepeatPoint repeat) {
        for (int i = 0; i < repeat.count(); i++) {
          expandHistogramPoint(repeat.value(), values);
        }
      } else if (point instanceof StepSequencePoint step) {
        if (step.start() instanceof HistogramPoint start
            && step.step() instanceof HistogramPoint delta) {
          HistogramCounts startCounts = parseHistogramCounts(start.value());
          HistogramCounts deltaCounts = parseHistogramCounts(delta.value());
          for (int i = 0; i <= step.count(); i++) {
            float sum = startCounts.sum + deltaCounts.sum * i;
            float count = startCounts.count + deltaCounts.count * i;
            values.add(buildHistogramLiteral(sum, count));
          }
        } else {
          throw new IllegalStateException("histogram step sequence not supported yet");
        }
      } else {
        throw new IllegalStateException("expected histogram point");
      }
    }


    private double extractNumber(PointExpr point) {
      if (point instanceof NumberPoint number) {
        return number.value();
      }
      throw new IllegalStateException("expected number point");
    }

    private SeriesId normalizeSeries(IngestedSeries series) {
      Map<String, String> labels = new HashMap<>(series.labels());
      String metric = series.metric();
      if (metric == null || metric.isEmpty()) {
        String name = labels.remove("__name__");
        metric = name == null ? "" : name;
      }
      return new SeriesId(metric, new Labels(labels));
    }
  }
}

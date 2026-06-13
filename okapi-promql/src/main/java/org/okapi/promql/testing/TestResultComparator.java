/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.testing;

import org.okapi.metrics.pojos.results.GaugeScan;
import org.okapi.promql.eval.*;
import org.okapi.promql.eval.ScalarResult;
import org.okapi.promql.eval.VectorData.SeriesId;
import org.okapi.promql.eval.VectorData.SeriesSample;
import org.okapi.promql.eval.VectorData.SeriesWindow;
import org.okapi.promql.testing.PromQlTestAst.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Compares a PromQL {@link ExpressionResult} against the expected results declared in an
 * {@link EvalCmd}. Returns a list of differences; empty means the eval passed.
 */
final class TestResultComparator {

  private static final double DEFAULT_EPSILON = 1e-5;

  List<TestExpectationDifference> compare(EvalCmd cmd, ExpressionResult result) {
    // Range evals produce InstantVectorResult stepped over time; compare as range.
    if (cmd.evalType() instanceof RangeEval && result instanceof InstantVectorResult iv) {
      return compareRangeFromInstant(cmd, iv);
    }
    return switch (result) {
      case ScalarResult s          -> compareScalar(cmd, s);
      case StringResult s          -> compareString(cmd, s);
      case InstantVectorResult iv  -> compareInstantVector(cmd, iv);
      case RangeVectorResult rv    -> compareRangeVector(cmd, rv);
      case HistogramVectorResult h -> compareHistogramVector(cmd, h);
      default -> List.of(TestExpectationDifference.of(
          cmd.expression(), "unsupported result type", null, result.toString()));
    };
  }

  private List<TestExpectationDifference> compareString(EvalCmd cmd, StringResult actual) {
    for (Expectation expectation : cmd.expectations()) {
      if (expectation instanceof ExpectString expected) {
        if (expected.value().equals(actual.getValue())) return List.of();
        return List.of(
            TestExpectationDifference.of(
                cmd.expression(), "string value mismatch", expected.value(), actual.getValue()));
      }
    }
    return List.of(
        TestExpectationDifference.of(
            cmd.expression(), "expected no string result but got value", "none", actual.getValue()));
  }

  // ---------- Scalar ----------

  private List<TestExpectationDifference> compareScalar(EvalCmd cmd, ScalarResult scalar) {
    List<ExpectedResult> expected = cmd.results();
    if (expected.isEmpty()) {
      return List.of(TestExpectationDifference.of(
          cmd.expression(), "expected no scalar result but got value", "none",
          String.valueOf(scalar.getValue())));
    }
    if (!(expected.get(0) instanceof PromQlTestAst.ScalarResult exp)) {
      return List.of(TestExpectationDifference.of(
          cmd.expression(), "expected scalar result but got series",
          expected.get(0).toString(), String.valueOf(scalar.getValue())));
    }
    if (!floatEquals((float) exp.value(), scalar.getValue())) {
      return List.of(TestExpectationDifference.of(
          cmd.expression(), "scalar value mismatch",
          String.valueOf(exp.value()), String.valueOf(scalar.getValue())));
    }
    return List.of();
  }

  // ---------- Instant vector ----------

  private List<TestExpectationDifference> compareInstantVector(EvalCmd cmd, InstantVectorResult iv) {
    Long evalTime = null;
    if (cmd.evalType() instanceof InstantEval instant && !cmd.expression().contains("@")) {
      evalTime = DurationParser.toMillis(instant.at().text());
    }

    Map<SeriesId, Float> actual = new HashMap<>();
    for (SeriesSample s : iv) {
      if (evalTime != null && s.sample().ts() != evalTime) continue;
      actual.put(s.series(), s.sample().value());
    }

    record ExpectedValue(float value, boolean histogram) {}
    Map<SeriesId, ExpectedValue> expected = new HashMap<>();
    for (ExpectedResult res : cmd.results()) {
      if (res instanceof SeriesResult sr) {
        List<Float> values = expandExpectedPoints(sr.series().points());
        float value = values.isEmpty() ? Float.NaN : values.get(0);
        expected.put(InMemoryTimeSeriesStore.normalize((SeriesDef) sr.series()),
            new ExpectedValue(value, containsHistogramPoint(sr.series().points())));
      }
    }

    List<TestExpectationDifference> diffs = new ArrayList<>();
    for (var entry : expected.entrySet()) {
      SeriesId id = entry.getKey();
      ExpectedValue exp = entry.getValue();
      Float actualValue = actual.get(id);
      if (actualValue == null) {
        if (!exp.histogram()) {
          diffs.add(TestExpectationDifference.of(cmd.expression(), "missing series", id.toString(), null));
        }
        continue;
      }
      if (!floatEquals(exp.value(), actualValue)) {
        diffs.add(TestExpectationDifference.of(cmd.expression(), "instant vector value mismatch",
            String.valueOf(exp.value()), String.valueOf(actualValue)));
      }
    }
    return diffs;
  }

  // ---------- Range from instant (RangeEval → InstantVectorResult) ----------

  private List<TestExpectationDifference> compareRangeFromInstant(EvalCmd cmd, InstantVectorResult iv) {
    Map<SeriesId, List<Float>> expected = new HashMap<>();
    for (ExpectedResult res : cmd.results()) {
      if (res instanceof SeriesResult sr) {
        expected.put(InMemoryTimeSeriesStore.normalize((SeriesDef) sr.series()),
            expandExpectedPointsForRange(sr.series().points()));
      }
    }

    RangeSpec range = rangeSpec(cmd);
    Map<SeriesId, Map<Long, Float>> actualBySeries = new HashMap<>();
    for (SeriesSample s : iv) {
      actualBySeries.computeIfAbsent(s.series(), k -> new HashMap<>())
          .put(s.sample().ts(), s.sample().value());
    }

    List<TestExpectationDifference> diffs = new ArrayList<>();
    for (var entry : expected.entrySet()) {
      SeriesId id = entry.getKey();
      List<Float> exp = entry.getValue();
      Map<Long, Float> actualSeries = actualBySeries.get(id);
      if (actualSeries == null) {
        diffs.add(TestExpectationDifference.of(cmd.expression(), "missing series", id.toString(), null));
        continue;
      }
      List<Float> actual = alignRangeValues(actualSeries, range);
      if (actual.size() != exp.size()) {
        diffs.add(TestExpectationDifference.of(cmd.expression(), "range vector length mismatch",
            String.valueOf(exp.size()), String.valueOf(actual.size())));
        continue;
      }
      for (int i = 0; i < actual.size(); i++) {
        if (!floatEquals(exp.get(i), actual.get(i))) {
          diffs.add(TestExpectationDifference.of(cmd.expression(),
              "range vector value mismatch at index " + i,
              String.valueOf(exp.get(i)), String.valueOf(actual.get(i))));
          break;
        }
      }
    }
    for (SeriesId id : actualBySeries.keySet()) {
      if (!expected.containsKey(id)) {
        diffs.add(TestExpectationDifference.of(cmd.expression(), "unexpected series", null, id.toString()));
      }
    }
    return diffs;
  }

  // ---------- Range vector ----------

  private List<TestExpectationDifference> compareRangeVector(EvalCmd cmd, RangeVectorResult rv) {
    Map<SeriesId, List<Float>> expected = new HashMap<>();
    for (ExpectedResult res : cmd.results()) {
      if (res instanceof SeriesResult sr) {
        expected.put(InMemoryTimeSeriesStore.normalize((SeriesDef) sr.series()),
            expandExpectedPointsForRange(sr.series().points()));
      }
    }

    RangeSpec range = rangeSpec(cmd);
    List<TestExpectationDifference> diffs = new ArrayList<>();
    for (SeriesWindow window : rv.data()) {
      SeriesId id = window.id();
      List<Float> exp = expected.get(id);
      if (exp == null) {
        diffs.add(TestExpectationDifference.of(cmd.expression(), "unexpected series", null, id.toString()));
        continue;
      }
      if (window.scan() instanceof GaugeScan gs) {
        List<Float> actual = alignRangeValues(gs, range, exp.size());
        if (actual.size() != exp.size()) {
          diffs.add(TestExpectationDifference.of(cmd.expression(), "range vector length mismatch",
              String.valueOf(exp.size()), String.valueOf(actual.size())));
          continue;
        }
        for (int i = 0; i < actual.size(); i++) {
          if (!floatEquals(exp.get(i), actual.get(i))) {
            diffs.add(TestExpectationDifference.of(cmd.expression(),
                "range vector value mismatch at index " + i,
                String.valueOf(exp.get(i)), String.valueOf(actual.get(i))));
            break;
          }
        }
      } else if (window.scan() instanceof HistogramSeries hs) {
        List<HistogramCounts> expHist = expectedHistogram(id, cmd);
        if (expHist == null) {
          diffs.add(TestExpectationDifference.of(cmd.expression(), "unexpected series", null, id.toString()));
          continue;
        }
        List<HistogramCounts> actual = alignHistogramValues(hs, range, expHist.size());
        compareHistogramList(cmd, expHist, actual, diffs);
      } else {
        diffs.add(TestExpectationDifference.of(cmd.expression(), "unsupported scan type", null,
            window.scan().getClass().getSimpleName()));
      }
    }
    return diffs;
  }

  // ---------- Histogram vector ----------

  private List<TestExpectationDifference> compareHistogramVector(EvalCmd cmd, HistogramVectorResult hv) {
    Map<SeriesId, HistogramVectorResult.HistogramValue> actual = new HashMap<>();
    for (var s : hv.data()) actual.put(s.id(), s.sample().value());

    Map<SeriesId, HistogramVectorResult.HistogramValue> expected = new HashMap<>();
    for (ExpectedResult res : cmd.results()) {
      if (res instanceof SeriesResult sr) {
        expected.put(InMemoryTimeSeriesStore.normalize((SeriesDef) sr.series()),
            extractExpectedHistogramValue(sr.series().points()));
      }
    }

    List<TestExpectationDifference> diffs = new ArrayList<>();
    for (var entry : expected.entrySet()) {
      SeriesId id = entry.getKey();
      var exp = entry.getValue();
      var act = actual.get(id);
      if (act == null) {
        diffs.add(TestExpectationDifference.of(cmd.expression(), "missing series", id.toString(), null));
        continue;
      }
      if (!floatEquals(exp.count(), act.count()) || !floatEquals(exp.sum(), act.sum())) {
        diffs.add(TestExpectationDifference.of(cmd.expression(), "histogram value mismatch",
            "count=" + exp.count() + " sum=" + exp.sum(),
            "count=" + act.count() + " sum=" + act.sum()));
      }
    }
    if (actual.size() != expected.size()) {
      diffs.add(TestExpectationDifference.of(cmd.expression(), "series count mismatch",
          String.valueOf(expected.size()), String.valueOf(actual.size())));
    }
    return diffs;
  }

  // ---------- Expected-value expansion (comparison semantics) ----------

  // MissingPoint → omit, StalePoint → null sentinel for "no value at step"
  private List<Float> expandExpectedPoints(List<PointExpr> points) {
    List<Float> out = new ArrayList<>();
    for (PointExpr p : points) expandExpected(p, out);
    return out;
  }

  private List<Float> expandExpectedPointsForRange(List<PointExpr> points) {
    List<Float> out = new ArrayList<>();
    for (PointExpr p : points) {
      if (p instanceof MissingPoint || p instanceof StalePoint) {
        out.add(Float.NaN);
      } else {
        expandExpected(p, out);
      }
    }
    return out;
  }

  private void expandExpected(PointExpr point, List<Float> out) {
    switch (point) {
      case NumberPoint np -> out.add((float) np.value());
      case NaNPoint ignored -> out.add(Float.NaN);
      case InfPoint ip -> out.add(ip.negative() ? Float.NEGATIVE_INFINITY : Float.POSITIVE_INFINITY);
      case MissingPoint mp -> { /* omit */ }
      case StalePoint st -> out.add(null);
      case RepeatPoint rp -> { for (int i = 0; i <= rp.count(); i++) expandExpected(rp.value(), out); }
      case StepSequencePoint sp -> {
        double start = extractNumber(sp.start()), delta = extractNumber(sp.step());
        for (int i = 0; i <= sp.count(); i++) out.add((float) (start + delta * i));
      }
      case HistogramPoint ignored -> out.add(Float.NaN);
    }
  }

  // ---------- Histogram helpers ----------

  private HistogramVectorResult.HistogramValue extractExpectedHistogramValue(List<PointExpr> points) {
    for (PointExpr p : points) {
      if (p instanceof HistogramPoint hp) {
        float count = InMemoryTimeSeriesStore.histogramField(hp.value(), "count");
        float sum = InMemoryTimeSeriesStore.histogramField(hp.value(), "sum");
        return new HistogramVectorResult.HistogramValue(count, sum);
      }
    }
    return new HistogramVectorResult.HistogramValue(Float.NaN, Float.NaN);
  }

  private List<HistogramCounts> expandExpectedHistogramPoints(List<PointExpr> points) {
    List<HistogramLiteral> literals = new ArrayList<>();
    for (PointExpr p : points) expandExpectedHistogramPoint(p, literals);
    List<HistogramCounts> out = new ArrayList<>();
    for (HistogramLiteral lit : literals) {
      out.add(lit == null ? null : new HistogramCounts(
          InMemoryTimeSeriesStore.histogramField(lit, "sum"),
          InMemoryTimeSeriesStore.histogramField(lit, "count")));
    }
    return out;
  }

  private void expandExpectedHistogramPoint(PointExpr point, List<HistogramLiteral> out) {
    switch (point) {
      case HistogramPoint hp -> out.add(hp.value());
      case MissingPoint mp -> out.add(null);
      case StalePoint st -> out.add(null);
      case RepeatPoint rp -> { for (int i = 0; i <= rp.count(); i++) expandExpectedHistogramPoint(rp.value(), out); }
      case StepSequencePoint sp -> {
        if (sp.start() instanceof HistogramPoint start && sp.step() instanceof HistogramPoint delta) {
          float startSum = InMemoryTimeSeriesStore.histogramField(start.value(), "sum");
          float startCount = InMemoryTimeSeriesStore.histogramField(start.value(), "count");
          float dSum = InMemoryTimeSeriesStore.histogramField(delta.value(), "sum");
          float dCount = InMemoryTimeSeriesStore.histogramField(delta.value(), "count");
          for (int i = 0; i <= sp.count(); i++) {
            out.add(InMemoryTimeSeriesStore.buildHistogramLiteral(
                startSum + dSum * i, startCount + dCount * i));
          }
        } else {
          throw new IllegalStateException("histogram step sequence: start and step must both be histogram points");
        }
      }
      default -> throw new IllegalStateException("expected histogram point");
    }
  }

  private List<HistogramCounts> expectedHistogram(SeriesId id, EvalCmd cmd) {
    for (ExpectedResult res : cmd.results()) {
      if (res instanceof SeriesResult sr
          && InMemoryTimeSeriesStore.normalize((SeriesDef) sr.series()).equals(id)) {
        return expandExpectedHistogramPoints(sr.series().points());
      }
    }
    return null;
  }

  private void compareHistogramList(EvalCmd cmd, List<HistogramCounts> exp,
      List<HistogramCounts> actual, List<TestExpectationDifference> diffs) {
    if (actual.size() != exp.size()) {
      diffs.add(TestExpectationDifference.of(cmd.expression(), "range vector length mismatch",
          String.valueOf(exp.size()), String.valueOf(actual.size())));
      return;
    }
    for (int i = 0; i < actual.size(); i++) {
      HistogramCounts e = exp.get(i), a = actual.get(i);
      if (e == null && a == null) continue;
      if (e == null || a == null || !floatEquals(e.sum, a.sum) || !floatEquals(e.count, a.count)) {
        diffs.add(TestExpectationDifference.of(cmd.expression(),
            "range vector value mismatch at index " + i,
            String.valueOf(e), String.valueOf(a)));
        break;
      }
    }
  }

  // ---------- Range alignment ----------

  private List<Float> alignRangeValues(GaugeScan scan, RangeSpec range) {
    return alignRangeValues(scan, range, range.steps);
  }

  private List<Float> alignRangeValues(GaugeScan scan, RangeSpec range, int steps) {
    if (range.stepMs <= 0 || range.steps <= 0) return new ArrayList<>(scan.getValues());
    Map<Long, Float> byTs = new HashMap<>();
    List<Long> ts = scan.getTimestamps();
    List<Float> vals = scan.getValues();
    for (int i = 0; i < ts.size(); i++) byTs.put(ts.get(i), vals.get(i));
    return alignRangeValues(byTs, range, steps);
  }

  private List<Float> alignRangeValues(Map<Long, Float> byTs, RangeSpec range) {
    return alignRangeValues(byTs, range, range.steps);
  }

  private List<Float> alignRangeValues(Map<Long, Float> byTs, RangeSpec range, int steps) {
    if (range.stepMs <= 0 || range.steps <= 0) return new ArrayList<>(byTs.values());
    List<Float> out = new ArrayList<>();
    long t = range.startMs;
    for (int i = 0; i < steps; i++) {
      Float v = byTs.get(t);
      out.add(v == null || Staleness.isStale(v) ? Float.NaN : v);
      t += range.stepMs;
    }
    return out;
  }

  private List<HistogramCounts> alignHistogramValues(HistogramSeries hs, RangeSpec range) {
    return alignHistogramValues(hs, range, range.steps);
  }

  private List<HistogramCounts> alignHistogramValues(HistogramSeries hs, RangeSpec range, int steps) {
    List<HistogramCounts> out = new ArrayList<>();
    if (range.stepMs <= 0 || range.steps <= 0) {
      for (var p : hs.getPoints())
        if (p instanceof HistogramSeries.HistogramSample histogram)
          out.add(new HistogramCounts((float) histogram.sum(), (float) histogram.count()));
      return out;
    }
    Map<Long, HistogramCounts> byTs = new HashMap<>();
    for (var p : hs.getPoints())
      if (p instanceof HistogramSeries.HistogramSample histogram)
        byTs.put(p.endMs(), new HistogramCounts((float) histogram.sum(), (float) histogram.count()));
    long t = range.startMs;
    for (int i = 0; i < steps; i++) {
      out.add(byTs.get(t));
      t += range.stepMs;
    }
    return out;
  }

  // ---------- RangeSpec ----------

  private RangeSpec rangeSpec(EvalCmd cmd) {
    ExpectRangeVector expectRange = firstExpectRange(cmd.expectations());
    if (expectRange != null) {
      long start = DurationParser.toMillis(expectRange.from().text());
      long end   = DurationParser.toMillis(expectRange.to().text());
      long step  = DurationParser.toMillis(expectRange.step().text());
      int steps  = step > 0 ? (int) ((end - start) / step) : 0;
      return new RangeSpec(start, end, step, steps);
    }
    if (cmd.evalType() instanceof RangeEval r) {
      long start = DurationParser.toMillis(r.from().text());
      long end   = DurationParser.toMillis(r.to().text());
      long step  = DurationParser.toMillis(r.step().text());
      int steps  = step > 0 ? (int) ((end - start) / step) + 1 : 0;
      return new RangeSpec(start, end, step, steps);
    }
    return new RangeSpec(0, 0, 0, 0);
  }

  private ExpectRangeVector firstExpectRange(List<Expectation> expectations) {
    for (Expectation e : expectations) if (e instanceof ExpectRangeVector r) return r;
    return null;
  }

  // ---------- Misc helpers ----------

  private boolean containsHistogramPoint(List<PointExpr> points) {
    for (PointExpr p : points) {
      if (p instanceof HistogramPoint) return true;
      if (p instanceof RepeatPoint rp && rp.value() instanceof HistogramPoint) return true;
      if (p instanceof StepSequencePoint sp
          && (sp.start() instanceof HistogramPoint || sp.step() instanceof HistogramPoint)) return true;
    }
    return false;
  }

  private static double extractNumber(PointExpr point) {
    if (point instanceof NumberPoint np) return np.value();
    throw new IllegalStateException("expected number point");
  }

  static boolean floatEquals(float a, float b) {
    if (Float.isNaN(a) && Float.isNaN(b)) return true;
    if (Float.isInfinite(a) || Float.isInfinite(b)) return a == b;
    double diff = Math.abs(a - b);
    double scale = Math.max(Math.abs(a), Math.abs(b));
    return scale == 0.0 ? diff <= DEFAULT_EPSILON : diff <= DEFAULT_EPSILON * scale;
  }

  private record RangeSpec(long startMs, long endMs, long stepMs, int steps) {}
  private record HistogramCounts(float sum, float count) {}
}

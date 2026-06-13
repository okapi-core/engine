/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.testing;

import java.util.List;
import java.util.Map;

public final class PromQlTestAst {
  private PromQlTestAst() {}

  public sealed interface Node
      permits TestFile,
          Command,
          EvalType,
          Expectation,
          ExpectedResult,
          PointExpr,
          HistogramValue,
          DurationLiteral,
          SeriesDef,
          HistogramLiteral {}

  public record TestFile(List<Command> commands) implements Node {
    public TestFile {
      commands = List.copyOf(commands);
    }
  }

  public sealed interface Command extends Node permits ClearCmd, LoadCmd, EvalCmd {}

  public record ClearCmd() implements Command {}

  public record LoadCmd(DurationLiteral step, boolean withNhcb, List<SeriesDef> series)
      implements Command {
    public LoadCmd {
      series = List.copyOf(series);
    }
  }

  public record EvalCmd(
      EvalType evalType,
      String expression,
      LegacyEvalModifier legacyModifier,
      List<Expectation> expectations,
      List<ExpectedResult> results)
      implements Command {
    public EvalCmd {
      expectations = List.copyOf(expectations);
      results = List.copyOf(results);
    }
  }

  public enum LegacyEvalModifier {
    NONE,
    FAIL,
    WARN,
    INFO,
    ORDERED
  }

  public sealed interface EvalType extends Node permits InstantEval, RangeEval {}

  public record InstantEval(DurationLiteral at) implements EvalType {}

  public record RangeEval(DurationLiteral from, DurationLiteral to, DurationLiteral step)
      implements EvalType {}

  public record DurationLiteral(String text) implements Node {}

  public sealed interface Expectation extends Node
      permits ExpectAnnotation, ExpectRangeVector, ExpectString, ExpectFailMessage, ExpectFailRegexp {}

  public record ExpectAnnotation(ExpectType type, MatchType matchType, String pattern)
      implements Expectation {}

  public record ExpectRangeVector(DurationLiteral from, DurationLiteral to, DurationLiteral step)
      implements Expectation {}

  public record ExpectString(String value) implements Expectation {}

  public record ExpectFailMessage(String message) implements Expectation {}

  public record ExpectFailRegexp(String pattern) implements Expectation {}

  public enum ExpectType {
    FAIL,
    WARN,
    INFO,
    NO_WARN,
    NO_INFO,
    ORDERED
  }

  public enum MatchType {
    MSG,
    REGEX
  }

  public sealed interface ExpectedResult extends Node permits ScalarResult, SeriesResult {}

  public record ScalarResult(double value) implements ExpectedResult {}

  public record SeriesResult(SeriesDef series) implements ExpectedResult {}

  public record SeriesDef(String metric, Map<String, String> labels, List<PointExpr> points)
      implements Node {
    public SeriesDef {
      points = List.copyOf(points);
    }
  }

  public sealed interface PointExpr extends Node
      permits NumberPoint,
          MissingPoint,
          StalePoint,
          NaNPoint,
          InfPoint,
          HistogramPoint,
          RepeatPoint,
          StepSequencePoint {}

  public record NumberPoint(double value) implements PointExpr {}

  public record MissingPoint() implements PointExpr {}

  public record StalePoint() implements PointExpr {}

  public record NaNPoint() implements PointExpr {}

  public record InfPoint(boolean negative) implements PointExpr {}

  public record HistogramPoint(HistogramLiteral value) implements PointExpr {}

  public record RepeatPoint(PointExpr value, int count) implements PointExpr {}

  public record StepSequencePoint(PointExpr start, PointExpr step, int count) implements PointExpr {}

  public record HistogramLiteral(Map<String, HistogramValue> fields) implements Node {}

  public sealed interface HistogramValue extends Node
      permits HistogramNumber, HistogramIdentifier, HistogramNumberList {}

  public record HistogramNumber(double value) implements HistogramValue {}

  public record HistogramIdentifier(String value) implements HistogramValue {}

  public record HistogramNumberList(List<Double> values) implements HistogramValue {
    public HistogramNumberList {
      values = List.copyOf(values);
    }
  }
}

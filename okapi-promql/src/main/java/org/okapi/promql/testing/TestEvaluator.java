/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.testing;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.okapi.Statistics;
import org.okapi.promql.eval.ExpressionEvaluator;
import org.okapi.promql.eval.ExpressionResult;
import org.okapi.promql.eval.exceptions.EvaluationException;
import org.okapi.promql.eval.ts.StatisticsMerger;
import org.okapi.promql.parser.PromQLLexer;
import org.okapi.promql.parser.PromQLParser;
import org.okapi.promql.testing.PromQlTestAst.*;

/**
 * Evaluates a parsed {@link TestFile} against the in-memory PromQL engine and returns any
 * expectation failures. Replaces {@code PromQlTestPipeline}.
 */
public final class TestEvaluator {

  private final InMemoryTimeSeriesStore store;
  private final ExpressionEvaluator promql;
  private final TestResultComparator comparator;

  public TestEvaluator() {
    this.store = new InMemoryTimeSeriesStore();
    ExecutorService exec = Executors.newFixedThreadPool(2);
    this.promql =
        new ExpressionEvaluator(store.tsClient, store.discovery, exec, NoopStatsMerger.INSTANCE);
    this.comparator = new TestResultComparator();
  }

  public List<TestExpectationDifference> run(String script) {
    return run(new PromQlTestDataParser(script).parse());
  }

  public List<TestExpectationDifference> run(TestFile file) {
    List<TestExpectationDifference> failures = new ArrayList<>();
    for (Command cmd : file.commands()) {
      switch (cmd) {
        case ClearCmd ignored -> store.clear();
        case LoadCmd l -> store.ingest(l);
        case EvalCmd e -> failures.addAll(evaluate(e));
      }
    }
    return failures;
  }

  // ---------- Eval dispatch ----------

  private List<TestExpectationDifference> evaluate(EvalCmd cmd) {
    ExpressionResult result = null;
    EvaluationException evalError = null;

    try {
      PromQLParser parser = buildParser(cmd.expression());
      result =
          switch (cmd.evalType()) {
            case InstantEval instant ->
                promql.evaluateAt(
                    cmd.expression(), DurationParser.toMillis(instant.at().text()), parser);
            case RangeEval range ->
                promql.evaluate(
                    cmd.expression(),
                    DurationParser.toMillis(range.from().text()),
                    DurationParser.toMillis(range.to().text()),
                    DurationParser.toMillis(range.step().text()),
                    parser);
          };
    } catch (EvaluationException ex) {
      evalError = ex;
    }

    // Check fail expectations first
    ExpectAnnotation failExpect = firstExpect(cmd.expectations(), ExpectType.FAIL);
    if (failExpect == null && cmd.legacyModifier() == LegacyEvalModifier.FAIL) {
      failExpect = new ExpectAnnotation(ExpectType.FAIL, null, null);
    }
    if (failExpect != null) {
      return checkFailExpectation(cmd, evalError, failExpect);
    }
    if (evalError != null) {
      return List.of(
          TestExpectationDifference.of(
              cmd.expression(), "unexpected evaluation error", null, evalError.getMessage()));
    }

    // Check unsupported annotations
    List<TestExpectationDifference> diffs = new ArrayList<>();
    for (Expectation exp : cmd.expectations()) {
      if (exp instanceof ExpectAnnotation ann) {
        switch (ann.type()) {
          case FAIL -> {
            /* handled above */
          }
          case NO_INFO, NO_WARN, INFO, WARN, ORDERED -> {
            /* not yet implemented, skip */
          }
        }
      }
    }

    if (result != null) diffs.addAll(comparator.compare(cmd, result));
    return diffs;
  }

  private List<TestExpectationDifference> checkFailExpectation(
      EvalCmd cmd, EvaluationException evalError, ExpectAnnotation failExpect) {
    if (evalError == null) {
      return List.of(
          TestExpectationDifference.of(
              cmd.expression(), "expected failure but evaluation succeeded", null, null));
    }
    if (failExpect.matchType() != null
        && failExpect.pattern() != null
        && !matchExpectation(evalError.getMessage(), failExpect)) {
      return List.of(
          TestExpectationDifference.of(
              cmd.expression(),
              "failure message mismatch",
              failExpect.pattern(),
              evalError.getMessage()));
    }
    return List.of();
  }

  // ---------- Helpers ----------

  private static PromQLParser buildParser(String expr) {
    return new PromQLParser(new CommonTokenStream(new PromQLLexer(CharStreams.fromString(expr))));
  }

  private static ExpectAnnotation firstExpect(List<Expectation> expectations, ExpectType type) {
    for (Expectation e : expectations) {
      if (e instanceof ExpectAnnotation ann && ann.type() == type) return ann;
    }
    return null;
  }

  private static boolean matchExpectation(String actual, ExpectAnnotation expect) {
    if (expect.matchType() == null || expect.pattern() == null) return true;
    return switch (expect.matchType()) {
      case MSG -> expect.pattern().equals(actual);
      case REGEX -> java.util.regex.Pattern.compile(expect.pattern()).matcher(actual).find();
    };
  }

  // ---------- NoopStatsMerger ----------

  private static final class NoopStatsMerger implements StatisticsMerger {
    static final NoopStatsMerger INSTANCE = new NoopStatsMerger();

    @Override
    public Statistics merge(Statistics a, Statistics b) {
      return a == null ? b : a;
    }
  }
}

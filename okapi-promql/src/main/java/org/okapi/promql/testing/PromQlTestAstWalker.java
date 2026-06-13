/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.testing;

import java.util.List;

import org.okapi.promql.testing.PromQlTestAst.*;

public final class PromQlTestAstWalker {
  private PromQlTestAstWalker() {}

  public static void walk(TestFile file, PromQlTestAstVisitor visitor) {
    visitor.visitTestFile(file);
    for (Command command : file.commands()) {
      walkCommand(command, visitor);
    }
  }

  private static void walkCommand(Command command, PromQlTestAstVisitor visitor) {
    if (command instanceof ClearCmd clear) {
      visitor.visitClearCmd(clear);
      return;
    }
    if (command instanceof LoadCmd load) {
      visitor.visitLoadCmd(load);
      for (SeriesDef series : load.series()) {
        visitor.visitSeriesDef(series);
        walkPoints(series.points(), visitor);
      }
      return;
    }
    if (command instanceof EvalCmd eval) {
      visitor.visitEvalCmd(eval);
      walkEvalType(eval.evalType(), visitor);
      for (Expectation expectation : eval.expectations()) {
        visitor.visitExpectation(expectation);
      }
      for (ExpectedResult result : eval.results()) {
        visitor.visitExpectedResult(result);
        if (result instanceof SeriesResult seriesResult) {
          SeriesDef series = seriesResult.series();
          visitor.visitSeriesDef(series);
          walkPoints(series.points(), visitor);
        }
      }
    }
  }

  private static void walkEvalType(EvalType evalType, PromQlTestAstVisitor visitor) {
    if (evalType instanceof InstantEval instant) {
      visitor.visitInstantEval(instant);
      return;
    }
    if (evalType instanceof RangeEval range) {
      visitor.visitRangeEval(range);
    }
  }

  private static void walkPoints(List<PointExpr> points, PromQlTestAstVisitor visitor) {
    for (PointExpr point : points) {
      visitor.visitPointExpr(point);
      if (point instanceof HistogramPoint histogramPoint) {
        visitor.visitHistogramLiteral(histogramPoint.value());
      }
    }
  }
}

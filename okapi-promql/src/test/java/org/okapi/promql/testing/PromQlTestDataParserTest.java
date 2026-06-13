/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.testing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.okapi.promql.testing.PromQlTestAst.*;

class PromQlTestDataParserTest {
  @Test
  void parsesLoadAndEval() {
    String input =
        """
        load 10s
          metric{env="prod"} 1 2 3

        eval instant at 10s metric
          expect ordered
          {__name__="metric",env="prod"} 2
        """;

    PromQlTestDataParser parser = new PromQlTestDataParser(input);
    TestFile file = parser.parse();

    assertEquals(2, file.commands().size());
    LoadCmd load = assertInstanceOf(LoadCmd.class, file.commands().get(0));
    assertEquals("10s", load.step().text());
    assertEquals(1, load.series().size());
    SeriesDef series = load.series().get(0);
    assertEquals("metric", series.metric());
    assertEquals(Map.of("env", "prod"), series.labels());
    assertEquals(3, series.points().size());

    EvalCmd eval = assertInstanceOf(EvalCmd.class, file.commands().get(1));
    assertInstanceOf(InstantEval.class, eval.evalType());
    assertEquals("metric", eval.expression());
    assertEquals(1, eval.expectations().size());
    assertEquals(1, eval.results().size());
  }

  @Test
  void parsesExpectStringAndScalarResult() {
    String input =
        """
        eval instant at 50m ("Foo")
          expect string "Foo"
          1
        """;

    PromQlTestDataParser parser = new PromQlTestDataParser(input);
    TestFile file = parser.parse();

    EvalCmd eval = assertInstanceOf(EvalCmd.class, file.commands().get(0));
    Expectation expectation = eval.expectations().get(0);
    ExpectString expectString = assertInstanceOf(ExpectString.class, expectation);
    assertEquals("Foo", expectString.value());

    ExpectedResult result = eval.results().get(0);
    ScalarResult scalar = assertInstanceOf(ScalarResult.class, result);
    assertEquals(1.0, scalar.value());
  }

  @Test
  void parsesHistogramPointsAndRepeat() {
    String input =
        """
        load 1m
          histo {{schema:0 sum:1 count:2 buckets:[1 2]}}x2
        """;

    PromQlTestDataParser parser = new PromQlTestDataParser(input);
    TestFile file = parser.parse();
    LoadCmd load = assertInstanceOf(LoadCmd.class, file.commands().get(0));
    SeriesDef series = load.series().get(0);
    PointExpr point = series.points().get(0);
    RepeatPoint repeat = assertInstanceOf(RepeatPoint.class, point);
    HistogramPoint histogram = assertInstanceOf(HistogramPoint.class, repeat.value());
    assertNotNull(histogram.value());
  }

  @Test
  void walkerInvokesVisitor() {
    String input =
        """
        clear
        eval instant at 1m up
          1
        """;

    PromQlTestDataParser parser = new PromQlTestDataParser(input);
    TestFile file = parser.parse();

    class CountingVisitor implements PromQlTestAstVisitor {
      int commands;

      @Override
      public void visitClearCmd(ClearCmd cmd) {
        commands++;
      }

      @Override
      public void visitEvalCmd(EvalCmd cmd) {
        commands++;
      }
    }

    CountingVisitor visitor = new CountingVisitor();
    PromQlTestAstWalker.walk(file, visitor);
    assertEquals(2, visitor.commands);
  }
}

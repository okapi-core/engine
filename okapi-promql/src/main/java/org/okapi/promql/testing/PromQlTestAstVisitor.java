/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.testing;

import org.okapi.promql.testing.PromQlTestAst.*;

public interface PromQlTestAstVisitor {
  default void visitTestFile(TestFile file) {}

  default void visitClearCmd(ClearCmd cmd) {}

  default void visitLoadCmd(LoadCmd cmd) {}

  default void visitEvalCmd(EvalCmd cmd) {}

  default void visitInstantEval(InstantEval eval) {}

  default void visitRangeEval(RangeEval eval) {}

  default void visitExpectation(Expectation expectation) {}

  default void visitExpectedResult(ExpectedResult result) {}

  default void visitSeriesDef(SeriesDef series) {}

  default void visitPointExpr(PointExpr point) {}

  default void visitHistogramLiteral(HistogramLiteral literal) {}
}

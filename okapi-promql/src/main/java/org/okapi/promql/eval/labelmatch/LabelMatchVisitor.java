/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.eval.labelmatch;

import java.util.ArrayList;
import java.util.Collections;
import org.okapi.promql.parse.LabelMatcher;
import org.okapi.promql.parse.LabelOp;
import org.okapi.promql.parser.PromQLParser;
import org.okapi.promql.parser.PromQLParserBaseVisitor;

/**
 * Visits a PromQL parse tree and extracts a single vector selector (instant or matrix, with/without
 * offset, optional parens) into a MetricMatchCondition.
 *
 * <p>Supported inputs: - METRIC_NAME { label matchers } - { label matchers } - METRIC_NAME [range],
 * {…}[range] - ( ... ) wrapper - ... with optional "offset <duration>" after the selector
 *
 * <p>Anything else (functions, binary ops, literals, etc.) is rejected with an error.
 */
public class LabelMatchVisitor extends PromQLParserBaseVisitor<LabelMatchCtx> {

  // ---- Entry points ---------------------------------------------------------

  private static String stripQuotes(String s) {
    if (s == null) return null;
    if (s.length() >= 2
        && (s.charAt(0) == '"' || s.charAt(0) == '\'')
        && s.charAt(s.length() - 1) == s.charAt(0)) {
      return s.substring(1, s.length() - 1).replace("\\\\", "\\");
    }
    return s;
  }

  // ---- Accept only plain selectors (possibly wrapped in parens/offset/range) --

  @Override
  public LabelMatchCtx visitExpression(PromQLParser.ExpressionContext ctx) {
    // expression : vectorOperation EOF ;
    return visit(ctx.vectorOperation());
  }

  @Override
  public LabelMatchCtx visitVecInstant(PromQLParser.VecInstantContext ctx) {
    // vector : instantSelector
    return visitInstantSelector(ctx.instantSelector());
  }

  @Override
  public LabelMatchCtx visitVecMatrix(PromQLParser.VecMatrixContext ctx) {
    // vector : matrixSelector
    // We ignore the range expression for discovery purposes; extract the base instantSelector.
    return visitInstantSelector(ctx.matrixSelector().instantSelector());
  }

  @Override
  public LabelMatchCtx visitVecOffset(PromQLParser.VecOffsetContext ctx) {
    // vector : offset
    // offset : instantSelector OFFSET DURATION | matrixSelector OFFSET DURATION
    if (ctx.offset().instantSelector() != null) {
      return visitInstantSelector(ctx.offset().instantSelector());
    } else {
      return visitInstantSelector(ctx.offset().matrixSelector().instantSelector());
    }
  }

  // ---- Explicit rejections for non-selector vectors -------------------------

  @Override
  public LabelMatchCtx visitVecParens(PromQLParser.VecParensContext ctx) {
    // vector : parens ; parens : '(' vectorOperation ')'
    // Just unwrap the parentheses and keep visiting down to a selector.
    return visit(ctx.parens().vectorOperation());
  }

  @Override
  public LabelMatchCtx visitVecFunc(PromQLParser.VecFuncContext ctx) {
    throw new IllegalArgumentException("match[] must be a vector selector, not a function");
  }

  @Override
  public LabelMatchCtx visitVecAgg(PromQLParser.VecAggContext ctx) {
    throw new IllegalArgumentException("match[] must be a vector selector, not an aggregation");
  }

  @Override
  public LabelMatchCtx visitVecLiteral(PromQLParser.VecLiteralContext ctx) {
    throw new IllegalArgumentException("match[] must be a vector selector, not a literal");
  }

  // Any binary operator form means it's not a single selector → reject early.
  @Override
  public LabelMatchCtx visitVecOpPow(PromQLParser.VecOpPowContext ctx) {
    rejectOp();
    return null;
  }

  @Override
  public LabelMatchCtx visitVecOpSubQuery(PromQLParser.VecOpSubQueryContext ctx) {
    rejectOp();
    return null;
  }

  @Override
  public LabelMatchCtx visitVecOpUnary(PromQLParser.VecOpUnaryContext ctx) {
    rejectOp();
    return null;
  }

  @Override
  public LabelMatchCtx visitVecOpMult(PromQLParser.VecOpMultContext ctx) {
    rejectOp();
    return null;
  }

  @Override
  public LabelMatchCtx visitVecOpAdd(PromQLParser.VecOpAddContext ctx) {
    rejectOp();
    return null;
  }

  @Override
  public LabelMatchCtx visitVecOpCompare(PromQLParser.VecOpCompareContext ctx) {
    rejectOp();
    return null;
  }

  @Override
  public LabelMatchCtx visitVecOpAddUnless(PromQLParser.VecOpAddUnlessContext ctx) {
    rejectOp();
    return null;
  }

  @Override
  public LabelMatchCtx visitVecOpOr(PromQLParser.VecOpOrContext ctx) {
    rejectOp();
    return null;
  }

  @Override
  public LabelMatchCtx visitVecOpMatch(PromQLParser.VecOpMatchContext ctx) {
    rejectOp();
    return null;
  }

  @Override
  public LabelMatchCtx visitVecOpAt(PromQLParser.VecOpAtContext ctx) {
    rejectOp();
    return null;
  }

  // ---- Selector + label matcher extraction ----------------------------------

  private void rejectOp() {
    throw new IllegalArgumentException("match[] must be a single vector selector (no operators)");
  }

  @Override
  public LabelMatchCtx visitInstantSelector(PromQLParser.InstantSelectorContext context) {
    if (context == null) {
      throw new IllegalArgumentException("match[]: missing selector");
    }
    final String metricName =
        (context.METRIC_NAME() != null) ? context.METRIC_NAME().getText() : null;
    final LabelConditionList matchers =
        (LabelConditionList) visitLabelMatcherList(context.labelMatcherList());
    return new MetricMatchCondition(metricName, matchers.getConditions());
  }

  @Override
  public LabelMatchCtx visitLabelMatcherList(PromQLParser.LabelMatcherListContext context) {
    if (context == null) {
      return new LabelConditionList(Collections.emptyList());
    }
    var conds = new ArrayList<LabelMatcher>();
    for (var lmCtx : context.labelMatcher()) {
      var cond = (LabelCondition) visitLabelMatcher(lmCtx);
      conds.add(cond.getLabelMatcher());
    }
    return new LabelConditionList(conds);
  }

  @Override
  public LabelMatchCtx visitLabelMatcher(PromQLParser.LabelMatcherContext ctx) {
    final String name = ctx.labelName().getText();
    final LabelOp op = getType(ctx.labelMatcherOperator());
    // STRING token includes quotes; strip them here.
    final String value = stripQuotes(ctx.STRING().getText());
    return new LabelCondition(new LabelMatcher(name, op, value));
  }

  // ---- helpers ---------------------------------------------------------------

  public LabelOp getType(PromQLParser.LabelMatcherOperatorContext operatorContext) {
    if (operatorContext.NE() != null) {
      return LabelOp.NE;
    } else if (operatorContext.EQ() != null) {
      return LabelOp.EQ;
    } else if (operatorContext.RE() != null) {
      return LabelOp.RE;
    } else if (operatorContext.NRE() != null) {
      return LabelOp.NRE;
    }
    throw new IllegalArgumentException("Unexpected label matcher operator");
  }
}

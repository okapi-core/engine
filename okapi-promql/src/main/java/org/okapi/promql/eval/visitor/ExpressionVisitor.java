/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.eval.visitor;

import java.util.*;
import org.okapi.promql.eval.nodes.*;
import org.okapi.promql.parse.LabelMatcher;
import org.okapi.promql.parse.LabelOp;
import org.okapi.promql.parser.PromQLParser;
import org.okapi.promql.parser.PromQLParserBaseVisitor;

public class ExpressionVisitor extends PromQLParserBaseVisitor<LogicalExpr> {

  @Override
  public LogicalExpr visitExpression(PromQLParser.ExpressionContext ctx) {
    return visit(ctx.vectorOperation());
  }

  // -------------------- Vector operations --------------------

  @Override
  public LogicalExpr visitVecOpPow(PromQLParser.VecOpPowContext ctx) {
    return binop("^", ctx.vectorOperation(0), ctx.vectorOperation(1), null, false);
  }

  @Override
  public LogicalExpr visitVecOpSubQuery(PromQLParser.VecOpSubQueryContext ctx) {
    var inner = visit(ctx.vectorOperation());
    var sq = ctx.subqueryOp();
    var range = sq.subqueryRange();
    long rangeMs;
    long stepMs;
    if (range.SUBQUERY_RANGE() != null) {
      String token = range.SUBQUERY_RANGE().getText();
      String body = token.substring(1, token.length() - 1);
      String[] parts = body.split(":");
      if (parts.length < 2) throw new IllegalArgumentException("subquery requires [range:step]");
      rangeMs = DurationUtil.parseToMillis(parts[0]);
      stepMs = DurationUtil.parseToMillis(parts[1]);
    } else {
      rangeMs = parseDurationLiteral(range.durationLiteral(0));
      if (range.durationLiteral(1) == null) {
        throw new IllegalArgumentException("subquery requires [range:step]");
      }
      stepMs = parseDurationLiteral(range.durationLiteral(1));
    }
    Long offMs = null;
    if (sq.offsetOp() != null) {
      offMs = parseDurationLiteral(sq.offsetOp().durationLiteral());
    }
    return new SubqueryExpr(inner, rangeMs, stepMs, offMs);
  }

  @Override
  public LogicalExpr visitVecOpUnary(PromQLParser.VecOpUnaryContext ctx) {
    LogicalExpr inner = visit(ctx.vectorOperation());
    if ("-".equals(ctx.unaryOp().getText()))
      return new BinaryOpExpr("*", new LiteralExpr(-1f), inner, null, false);
    return inner;
  }

  @Override
  public LogicalExpr visitVecOpMult(PromQLParser.VecOpMultContext ctx) {
    String op = ctx.multOp().getChild(0).getText();
    MatchSpec ms = buildMatchSpec(ctx.multOp().grouping());
    return binop(op, ctx.vectorOperation(0), ctx.vectorOperation(1), ms, false);
  }

  @Override
  public LogicalExpr visitVecOpAdd(PromQLParser.VecOpAddContext ctx) {
    String op = ctx.addOp().getChild(0).getText();
    MatchSpec ms = buildMatchSpec(ctx.addOp().grouping());
    return binop(op, ctx.vectorOperation(0), ctx.vectorOperation(1), ms, false);
  }

  @Override
  public LogicalExpr visitVecOpCompare(PromQLParser.VecOpCompareContext ctx) {
    String op = ctx.compareOp().getChild(0).getText();
    boolean bool = ctx.compareOp().BOOL() != null;
    MatchSpec ms = buildMatchSpec(ctx.compareOp().grouping());
    return binop(op, ctx.vectorOperation(0), ctx.vectorOperation(1), ms, bool);
  }

  @Override
  public LogicalExpr visitVecOpAddUnless(PromQLParser.VecOpAddUnlessContext ctx) {
    String op = ctx.andUnlessOp().getChild(0).getText();
    MatchSpec ms = buildMatchSpec(ctx.andUnlessOp().grouping());
    return binop(op, ctx.vectorOperation(0), ctx.vectorOperation(1), ms, false);
  }

  @Override
  public LogicalExpr visitVecOpOr(PromQLParser.VecOpOrContext ctx) {
    MatchSpec ms = buildMatchSpec(ctx.orOp().grouping());
    return binop("or", ctx.vectorOperation(0), ctx.vectorOperation(1), ms, false);
  }

  @Override
  public LogicalExpr visitVecOpMatch(PromQLParser.VecOpMatchContext ctx) {
    throw new UnsupportedOperationException("vectorMatchOp is not supported in this evaluator");
  }

  @Override
  public LogicalExpr visitVecOpAt(PromQLParser.VecOpAtContext ctx) {
    var left = visit(ctx.vectorOperation());
    var right = parseAtValue(ctx.atValue());
    if (ctx.offsetOp() != null) {
      long offMs = parseDurationLiteral(ctx.offsetOp().durationLiteral());
      left = applyOffset(left, offMs);
    }
    return new AtExpr(left, right);
  }

  @Override
  public LogicalExpr visitVecOpvec(PromQLParser.VecOpvecContext ctx) {
    return visit(ctx.vector());
  }

  // -------------------- Vector leaves --------------------

  @Override
  public LogicalExpr visitVecParens(PromQLParser.VecParensContext ctx) {
    return visit(ctx.parens().vectorOperation());
  }

  @Override
  public LogicalExpr visitVecLiteral(PromQLParser.VecLiteralContext ctx) {
    String lit = ctx.literal().getText();
    if (lit.startsWith("\""))
      return new LiteralExpr(0f); // strings not yet surfaced as string results
    if (isDurationLiteral(lit)) {
      long ms = DurationUtil.parseToMillis(lit);
      return new LiteralExpr((float) (ms / 1000.0d));
    }
    return new LiteralExpr(Float.parseFloat(lit));
  }

  @Override
  public LogicalExpr visitVecInstant(PromQLParser.VecInstantContext ctx) {
    return new InstantizeExpr(buildInstantSelector(ctx.instantSelector()));
  }

  @Override
  public LogicalExpr visitVecMatrix(PromQLParser.VecMatrixContext ctx) {
    var ms = ctx.matrixSelector();
    var sel = buildInstantSelector(ms.instantSelector());
    long rangeMs = parseTimeRange(ms.timeRange());
    return new RangeSelectorExpr((SelectorExpr) sel, rangeMs, null);
  }

  @Override
  public LogicalExpr visitVecOffset(PromQLParser.VecOffsetContext ctx) {
    long offMs = parseDurationLiteral(ctx.offset().durationLiteral());
    if (ctx.offset().instantSelector() != null) {
      var base = (SelectorExpr) buildInstantSelector(ctx.offset().instantSelector());
      return new InstantizeExpr(new SelectorExpr(base.metricOrNull, base.matchers, base.atTsMs, offMs));
    } else {
      var ms = ctx.offset().matrixSelector();
      var base = (SelectorExpr) buildInstantSelector(ms.instantSelector());
      long rangeMs = parseTimeRange(ms.timeRange());
      return new RangeSelectorExpr(base, rangeMs, offMs);
    }
  }

  @Override
  public LogicalExpr visitVecFunc(PromQLParser.VecFuncContext ctx) {
    String name;
    if (ctx.function_().FUNCTION() != null) {
      name = ctx.function_().FUNCTION().getText();
    } else {
      name = ctx.function_().METRIC_NAME().getText();
    }
    List<LogicalExpr> args = new ArrayList<>();
    var params = ctx.function_().parameter();
    if (params != null) {
      for (var p : params) {
        if (p.literal() != null) args.add(literalParam(p.literal()));
        else args.add(visit(p.vectorOperation()));
      }
    }
    return new FunctionExpr(name, args);
  }

  @Override
  public LogicalExpr visitVecAgg(PromQLParser.VecAggContext ctx) {
    var ag = ctx.aggregation();
    String op = ag.AGGREGATION_OPERATOR().getText();

    boolean isBy = false;
    List<String> labels = null; // null = no modifier: aggregate all series into one group
    if (ag.by() != null) {
      isBy = true;
      labels = labelList(ag.by().labelNameList());
    } else if (ag.without() != null) {
      labels = labelList(ag.without().labelNameList());
    }

    List<LogicalExpr> args = new ArrayList<>();
    for (var p : ag.parameterList().parameter()) {
      if (p.literal() != null) args.add(literalParam(p.literal()));
      else args.add(visit(p.vectorOperation()));
    }
    return new AggregateExpr(op, isBy, labels, args);
  }

  private long parseDurationLiteral(PromQLParser.DurationLiteralContext ctx) {
    boolean negative = ctx.SUB() != null;
    String text;
    if (ctx.DURATION() != null) {
      text = ctx.DURATION().getText();
    } else {
      text = ctx.NUMBER().getText();
    }
    long ms = DurationUtil.parseToMillis(text);
    return negative ? -ms : ms;
  }

  private long parseTimeRange(PromQLParser.TimeRangeContext ctx) {
    if (ctx.TIME_RANGE() != null) {
      String tr = ctx.TIME_RANGE().getText();
      return DurationUtil.parseToMillis(tr.substring(1, tr.length() - 1));
    }
    return parseDurationLiteral(ctx.durationLiteral());
  }

  private LogicalExpr parseAtValue(PromQLParser.AtValueContext ctx) {
    if (ctx.DURATION() != null) {
      long ms = DurationUtil.parseToMillis(ctx.DURATION().getText());
      return new LiteralExpr((float) (ms / 1000.0d));
    }
    return new LiteralExpr(Float.parseFloat(ctx.NUMBER().getText()));
  }

  private LogicalExpr applyOffset(LogicalExpr expr, long offMs) {
    if (expr instanceof InstantizeExpr ie) {
      return new InstantizeExpr(applyOffset(ie.inner, offMs));
    }
    if (expr instanceof SelectorExpr se) {
      return new SelectorExpr(se.metricOrNull, se.matchers, se.atTsMs, offMs);
    }
    if (expr instanceof RangeSelectorExpr rse) {
      return new RangeSelectorExpr(rse.base, rse.rangeMs, offMs);
    }
    if (expr instanceof SubqueryExpr sq) {
      return new SubqueryExpr(sq.inner, sq.rangeMs, sq.stepMs, offMs);
    }
    throw new IllegalArgumentException("offset modifier can only apply to selectors");
  }

  private boolean isDurationLiteral(String lit) {
    for (int i = 0; i < lit.length(); i++) {
      char c = lit.charAt(i);
      if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
        return true;
      }
    }
    return false;
  }

  // -------------------- helpers --------------------

  private LogicalExpr binop(
      String op,
      PromQLParser.VectorOperationContext left,
      PromQLParser.VectorOperationContext right,
      MatchSpec ms,
      boolean boolModifier) {
    return new BinaryOpExpr(op, visit(left), visit(right), ms, boolModifier);
  }

  private LogicalExpr literalParam(PromQLParser.LiteralContext lit) {
    String s = lit.getText();
    if (s.startsWith("\"")) return new StringLiteralExpr(stripQuotes(s));
    return new LiteralExpr(Float.parseFloat(s));
  }

  private SelectorExpr buildInstantSelector(PromQLParser.InstantSelectorContext is) {
    String metric = null;
    List<LabelMatcher> matchers = new ArrayList<>();
    if (is.METRIC_NAME() != null) {
      metric = is.METRIC_NAME().getText();
      if (is.LEFT_BRACE() != null) matchers = collectMatchers(is.labelMatcherList());
    } else {
      matchers = collectMatchers(is.labelMatcherList());
    }
    return new SelectorExpr(metric, matchers, null, null);
  }

  private List<LabelMatcher> collectMatchers(PromQLParser.LabelMatcherListContext list) {
    List<LabelMatcher> res = new ArrayList<>();
    if (list == null) return res;
    for (var lm : list.labelMatcher()) {
      String name = lm.labelName().getText();
      String val = stripQuotes(lm.STRING().getText());
      LabelOp op =
          switch (lm.labelMatcherOperator().getText()) {
            case "="  -> LabelOp.EQ;
            case "!=" -> LabelOp.NE;
            case "=~" -> LabelOp.RE;
            case "!~" -> LabelOp.NRE;
            default -> throw new IllegalArgumentException("unknown label op");
          };
      res.add(new LabelMatcher(name, op, val));
    }
    return res;
  }

  private List<String> labelList(PromQLParser.LabelNameListContext ctx) {
    List<String> ls = new ArrayList<>();
    if (ctx == null) return ls;
    for (var n : ctx.labelName()) ls.add(n.getText());
    return ls;
  }

  private String stripQuotes(String s) {
    if (s.length() >= 2 && (s.startsWith("\"") || s.startsWith("`")))
      return s.substring(1, s.length() - 1);
    return s;
  }

  private MatchSpec buildMatchSpec(PromQLParser.GroupingContext g) {
    if (g == null) return null;
    boolean groupLeft = g.groupLeft() != null;
    boolean groupRight = g.groupRight() != null;
    boolean hasOn = g.on_() != null;
    MatchSpec.Mode mode = hasOn ? MatchSpec.Mode.ON : MatchSpec.Mode.IGNORING;
    List<String> labels = hasOn ? labelList(g.on_().labelNameList()) : labelList(g.ignoring().labelNameList());
    List<String> include = new ArrayList<>();
    if (groupLeft && g.groupLeft().labelNameList() != null)
      include = labelList(g.groupLeft().labelNameList());
    if (groupRight && g.groupRight().labelNameList() != null)
      include = labelList(g.groupRight().labelNameList());
    return new MatchSpec(mode, labels, groupLeft, groupRight, include);
  }
}

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
    return binop(
        "^",
        ctx.vectorOperation(0),
        ctx.vectorOperation(1),
        null,
        false,
        buildFillSpec(ctx.powOp().fillModifier()));
  }

  @Override
  public LogicalExpr visitVecOpSubQuery(PromQLParser.VecOpSubQueryContext ctx) {
    var inner = visit(ctx.vectorOperation());
    var sq = ctx.subqueryOp();
    var range = sq.subqueryRange();
    DurationExpr rangeExpr = parseDurationExpr(range.durationExpr(0));
    DurationExpr stepExpr =
        range.durationExpr(1) == null
            ? new DurationExpr.Function("step", List.of())
            : parseDurationExpr(range.durationExpr(1));
    DurationExpr offsetExpr = null;
    if (sq.offsetOp() != null) {
      offsetExpr = parseOffsetDurationExpr(sq.offsetOp().offsetDurationExpr());
    }
    return new SubqueryExpr(inner, rangeExpr, stepExpr, offsetExpr);
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
    return binop(
        op,
        ctx.vectorOperation(0),
        ctx.vectorOperation(1),
        ms,
        false,
        buildFillSpec(ctx.multOp().fillModifier()));
  }

  @Override
  public LogicalExpr visitVecOpAdd(PromQLParser.VecOpAddContext ctx) {
    String op = ctx.addOp().getChild(0).getText();
    MatchSpec ms = buildMatchSpec(ctx.addOp().grouping());
    return binop(
        op,
        ctx.vectorOperation(0),
        ctx.vectorOperation(1),
        ms,
        false,
        buildFillSpec(ctx.addOp().fillModifier()));
  }

  @Override
  public LogicalExpr visitVecOpCompare(PromQLParser.VecOpCompareContext ctx) {
    String op = ctx.compareOp().getChild(0).getText();
    boolean bool = ctx.compareOp().BOOL() != null;
    MatchSpec ms = buildMatchSpec(ctx.compareOp().grouping());
    return binop(
        op,
        ctx.vectorOperation(0),
        ctx.vectorOperation(1),
        ms,
        bool,
        buildFillSpec(ctx.compareOp().fillModifier()));
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
      DurationExpr offset = parseOffsetDurationExpr(ctx.offsetOp().offsetDurationExpr());
      left = applyOffset(left, offset);
    }
    return new AtExpr(left, right);
  }

  @Override
  public LogicalExpr visitVecOpSmoothed(PromQLParser.VecOpSmoothedContext ctx) {
    return new SmoothedExpr(visit(ctx.vectorOperation()));
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
    return literalParam(ctx.literal());
  }

  @Override
  public LogicalExpr visitVecInstant(PromQLParser.VecInstantContext ctx) {
    return new InstantizeExpr(buildInstantSelector(ctx.instantSelector()));
  }

  @Override
  public LogicalExpr visitVecMatrix(PromQLParser.VecMatrixContext ctx) {
    var ms = ctx.matrixSelector();
    var sel = buildInstantSelector(ms.instantSelector());
    DurationExpr range = parseTimeRange(ms.timeRange());
    return new RangeSelectorExpr((SelectorExpr) sel, range, null, extendedVectorMode(ms));
  }

  @Override
  public LogicalExpr visitVecOffset(PromQLParser.VecOffsetContext ctx) {
    DurationExpr offset = parseOffsetDurationExpr(ctx.offset().offsetDurationExpr());
    if (ctx.offset().instantSelector() != null) {
      var base = (SelectorExpr) buildInstantSelector(ctx.offset().instantSelector());
      return new InstantizeExpr(new SelectorExpr(base.metricOrNull, base.matchers, base.atTsMs, offset));
    } else {
      var ms = ctx.offset().matrixSelector();
      var base = (SelectorExpr) buildInstantSelector(ms.instantSelector());
      DurationExpr range = parseTimeRange(ms.timeRange());
      return new RangeSelectorExpr(base, range, offset, extendedVectorMode(ms));
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

  private DurationExpr parseDurationExpr(PromQLParser.DurationExprContext ctx) {
    return parseDurationAddExpr(ctx.durationAddExpr());
  }

  private DurationExpr parseDurationAddExpr(PromQLParser.DurationAddExprContext ctx) {
    DurationExpr expr = parseDurationMultExpr(ctx.durationMultExpr(0));
    for (int i = 1; i < ctx.durationMultExpr().size(); i++) {
      expr =
          new DurationExpr.Binary(
              ctx.getChild(2 * i - 1).getText(), expr, parseDurationMultExpr(ctx.durationMultExpr(i)));
    }
    return expr;
  }

  private DurationExpr parseDurationMultExpr(PromQLParser.DurationMultExprContext ctx) {
    DurationExpr expr = parseDurationUnaryExpr(ctx.durationUnaryExpr(0));
    for (int i = 1; i < ctx.durationUnaryExpr().size(); i++) {
      expr =
          new DurationExpr.Binary(
              ctx.getChild(2 * i - 1).getText(), expr, parseDurationUnaryExpr(ctx.durationUnaryExpr(i)));
    }
    return expr;
  }

  private DurationExpr parseDurationPowExpr(PromQLParser.DurationPowExprContext ctx) {
    DurationExpr expr = parseDurationPrimaryExpr(ctx.durationPrimaryExpr());
    return ctx.durationUnaryExpr() == null
        ? expr
        : new DurationExpr.Binary("^", expr, parseDurationUnaryExpr(ctx.durationUnaryExpr()));
  }

  private DurationExpr parseDurationUnaryExpr(PromQLParser.DurationUnaryExprContext ctx) {
    if (ctx.durationUnaryExpr() != null) {
      return new DurationExpr.Unary(ctx.getChild(0).getText(), parseDurationUnaryExpr(ctx.durationUnaryExpr()));
    }
    return parseDurationPowExpr(ctx.durationPowExpr());
  }

  private DurationExpr parseDurationPrimaryExpr(PromQLParser.DurationPrimaryExprContext ctx) {
    if (ctx.DURATION() != null) return durationLiteral(ctx.DURATION().getText());
    if (ctx.NUMBER() != null) return durationLiteral(ctx.NUMBER().getText());
    if (ctx.durationFunction() != null) return parseDurationFunction(ctx.durationFunction());
    return parseDurationExpr(ctx.durationExpr());
  }

  private DurationExpr parseDurationFunction(PromQLParser.DurationFunctionContext ctx) {
    String name = ctx.getChild(0).getText();
    List<DurationExpr> args = new ArrayList<>();
    for (var arg : ctx.durationExpr()) args.add(parseDurationExpr(arg));
    return new DurationExpr.Function(name, args);
  }

  private DurationExpr parseOffsetDurationExpr(PromQLParser.OffsetDurationExprContext ctx) {
    DurationExpr expr = parseDurationPrimaryExpr(ctx.durationPrimaryExpr());
    return ctx.ADD() == null && ctx.SUB() == null
        ? expr
        : new DurationExpr.Unary(ctx.getChild(0).getText(), expr);
  }

  private DurationExpr durationLiteral(String text) {
    return new DurationExpr.Literal(DurationUtil.parseToMillis(text) / 1000d);
  }

  private DurationExpr parseTimeRange(PromQLParser.TimeRangeContext ctx) {
    return parseDurationExpr(ctx.durationExpr());
  }

  private LogicalExpr parseAtValue(PromQLParser.AtValueContext ctx) {
    if (ctx.DURATION() != null) {
      long ms = DurationUtil.parseToMillis(ctx.DURATION().getText());
      return new LiteralExpr(ms / 1000.0d);
    }
    return new LiteralExpr(Double.parseDouble(ctx.NUMBER().getText()));
  }

  private LogicalExpr applyOffset(LogicalExpr expr, DurationExpr offset) {
    if (expr instanceof InstantizeExpr ie) {
      return new InstantizeExpr(applyOffset(ie.inner, offset));
    }
    if (expr instanceof SelectorExpr se) {
      return new SelectorExpr(se.metricOrNull, se.matchers, se.atTsMs, offset);
    }
    if (expr instanceof RangeSelectorExpr rse) {
      return new RangeSelectorExpr(rse.base, rse.range, offset, rse.mode);
    }
    if (expr instanceof SubqueryExpr sq) {
      return new SubqueryExpr(sq.inner, sq.range, sq.step, offset);
    }
    throw new IllegalArgumentException("offset modifier can only apply to selectors");
  }

  private ExtendedVectorMode extendedVectorMode(PromQLParser.MatrixSelectorContext ctx) {
    if (ctx.extendedVectorModifier() == null) return ExtendedVectorMode.NONE;
    return ctx.extendedVectorModifier().ANCHORED() != null
        ? ExtendedVectorMode.ANCHORED
        : ExtendedVectorMode.SMOOTHED;
  }

  // -------------------- helpers --------------------

  private LogicalExpr binop(
      String op,
      PromQLParser.VectorOperationContext left,
      PromQLParser.VectorOperationContext right,
      MatchSpec ms,
      boolean boolModifier) {
    return binop(op, left, right, ms, boolModifier, FillSpec.none());
  }

  private LogicalExpr binop(
      String op,
      PromQLParser.VectorOperationContext left,
      PromQLParser.VectorOperationContext right,
      MatchSpec ms,
      boolean boolModifier,
      FillSpec fillSpec) {
    return new BinaryOpExpr(op, visit(left), visit(right), ms, boolModifier, fillSpec);
  }

  private LogicalExpr literalParam(PromQLParser.LiteralContext lit) {
    String s = lit.getText();
    if (s.startsWith("\"")) return new StringLiteralExpr(stripQuotes(s));
    if ("nan".equalsIgnoreCase(s)) return new LiteralExpr(Float.NaN);
    if ("inf".equalsIgnoreCase(s)) return new LiteralExpr(Float.POSITIVE_INFINITY);
    if (lit.DURATION() != null)
      return new LiteralExpr(DurationUtil.parseToMillis(s) / 1000.0d);
    return new LiteralExpr(Double.parseDouble(s));
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
    return new SelectorExpr(metric, matchers, null, (DurationExpr) null);
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
    if (s.length() >= 2 && (s.startsWith("\"") || s.startsWith("'") || s.startsWith("`")))
      return unescape(s.substring(1, s.length() - 1));
    return s;
  }

  private String unescape(String s) {
    return s.replace("\\\\", "\\");
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

  private FillSpec buildFillSpec(List<PromQLParser.FillModifierContext> modifiers) {
    Float left = null;
    Float right = null;
    for (var modifier : modifiers) {
      String name = modifier.METRIC_NAME().getText().toLowerCase(Locale.ROOT);
      float value = parseSignedFillLiteral(modifier.signedFillLiteral());
      switch (name) {
        case "fill" -> {
          left = value;
          right = value;
        }
        case "fill_left" -> left = value;
        case "fill_right" -> right = value;
        default -> throw new IllegalArgumentException("unknown binary fill modifier: " + name);
      }
    }
    return new FillSpec(left, right);
  }

  private float parseSignedFillLiteral(PromQLParser.SignedFillLiteralContext ctx) {
    String value = ctx.getText();
    if ("nan".equalsIgnoreCase(value)) return Float.NaN;
    if ("inf".equalsIgnoreCase(value) || "+inf".equalsIgnoreCase(value))
      return Float.POSITIVE_INFINITY;
    if ("-inf".equalsIgnoreCase(value)) return Float.NEGATIVE_INFINITY;
    return Float.parseFloat(value);
  }
}

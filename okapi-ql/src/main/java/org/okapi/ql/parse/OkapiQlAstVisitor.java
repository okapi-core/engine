/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.ql.parse;

import java.util.List;
import org.antlr.v4.runtime.tree.ParseTree;
import org.okapi.ql.ast.*;
import org.okapi.ql.parser.OkapiLogQlLexer;
import org.okapi.ql.parser.OkapiLogQlParser;
import org.okapi.ql.parser.OkapiLogQlParserBaseVisitor;

final class OkapiQlAstVisitor extends OkapiLogQlParserBaseVisitor<Object> {

  @Override
  public QueryLogQueryExpr visitQuery(OkapiLogQlParser.QueryContext ctx) {
    LogQueryExpr with = ctx.withClause() == null ? null : visitWithClause(ctx.withClause());
    LogQueryExpr filter = ctx.booleanExpr() == null ? null : visitBooleanExpr(ctx.booleanExpr());
    var pipeline = ctx.pipelineOp().stream().map(this::visitPipelineOp).toList();
    return new QueryLogQueryExpr(with, filter, pipeline);
  }

  @Override
  public LogQueryExpr visitWithClause(OkapiLogQlParser.WithClauseContext ctx) {
    return visitBooleanExpr(ctx.booleanExpr());
  }

  @Override
  public LogQueryExpr visitBooleanExpr(OkapiLogQlParser.BooleanExprContext ctx) {
    return visitOrExpr(ctx.orExpr());
  }

  @Override
  public LogQueryExpr visitOrExpr(OkapiLogQlParser.OrExprContext ctx) {
    var children = ctx.andExpr().stream().map(this::visitAndExpr).toList();
    return children.size() == 1 ? children.getFirst() : new OrLogQueryExpr(children);
  }

  @Override
  public LogQueryExpr visitAndExpr(OkapiLogQlParser.AndExprContext ctx) {
    var children = ctx.unaryExpr().stream().map(this::visitUnaryExpr).toList();
    return children.size() == 1 ? children.getFirst() : new AndLogQueryExpr(children);
  }

  @Override
  public LogQueryExpr visitUnaryExpr(OkapiLogQlParser.UnaryExprContext ctx) {
    if (ctx.NOT() != null) {
      return new NotLogQueryExpr(visitUnaryExpr(ctx.unaryExpr()));
    }
    return visitPrimaryExpr(ctx.primaryExpr());
  }

  @Override
  public LogQueryExpr visitPrimaryExpr(OkapiLogQlParser.PrimaryExprContext ctx) {
    if (ctx.comparison() != null) {
      return visitComparison(ctx.comparison());
    }
    if (ctx.freeText() != null) {
      return visitFreeText(ctx.freeText());
    }
    return visitBooleanExpr(ctx.booleanExpr());
  }

  @Override
  public ComparisonLogQueryExpr visitComparison(OkapiLogQlParser.ComparisonContext ctx) {
    var field = visitFieldRef(ctx.fieldRef());
    if (ctx.comparisonOp() != null) {
      return new ComparisonLogQueryExpr(
          field, comparisonOp(ctx.comparisonOp()), List.of(visitValue(ctx.value())));
    }
    if (ctx.IN() != null) {
      var op = ctx.NOT() == null ? ComparisonOp.IN : ComparisonOp.NOT_IN;
      return new ComparisonLogQueryExpr(field, op, visitValueList(ctx.valueList()));
    }
    var op = ctx.NOT() == null ? ComparisonOp.EXISTS : ComparisonOp.NOT_EXISTS;
    return new ComparisonLogQueryExpr(field, op, List.of());
  }

  @Override
  public FreeTextLogQueryExpr visitFreeText(OkapiLogQlParser.FreeTextContext ctx) {
    return new FreeTextLogQueryExpr(visitValue(ctx.value()));
  }

  @Override
  public List<LiteralValue> visitValueList(OkapiLogQlParser.ValueListContext ctx) {
    return ctx.value().stream().map(this::visitValue).toList();
  }

  @Override
  public LiteralValue visitValue(OkapiLogQlParser.ValueContext ctx) {
    if (ctx.stringLiteral() != null) {
      return new StringValue(unquote(ctx.stringLiteral().getText()));
    }
    if (ctx.numberLiteral() != null) {
      return visitNumberLiteral(ctx.numberLiteral());
    }
    if (ctx.durationLiteral() != null) {
      return visitDurationLiteral(ctx.durationLiteral());
    }
    if (ctx.timestampExpr() != null) {
      return visitTimestampExpr(ctx.timestampExpr());
    }
    return new IdentifierValue(ctx.IDENTIFIER().getText());
  }

  @Override
  public LiteralValue visitTimestampExpr(OkapiLogQlParser.TimestampExprContext ctx) {
    if (ctx.NOW() != null) {
      String sign = null;
      if (ctx.PLUS() != null) {
        sign = "+";
      } else if (ctx.MINUS() != null) {
        sign = "-";
      }
      var offset =
          ctx.durationLiteral() == null ? null : visitDurationLiteral(ctx.durationLiteral());
      return new NowValue(sign, offset);
    }
    return new TimestampValue(ctx.ISO_TIMESTAMP().getText());
  }

  @Override
  public DurationValue visitDurationLiteral(OkapiLogQlParser.DurationLiteralContext ctx) {
    return new DurationValue(ctx.DURATION().getText());
  }

  @Override
  public LiteralValue visitNumberLiteral(OkapiLogQlParser.NumberLiteralContext ctx) {
    if (ctx.INTEGER() != null) {
      return new IntegerValue(Long.parseLong(ctx.INTEGER().getText()));
    }
    return new DecimalValue(Double.parseDouble(ctx.DECIMAL().getText()));
  }

  @Override
  public PipelineOp visitPipelineOp(OkapiLogQlParser.PipelineOpContext ctx) {
    if (ctx.selectOp() != null) {
      return visitSelectOp(ctx.selectOp());
    }
    if (ctx.removeOp() != null) {
      return visitRemoveOp(ctx.removeOp());
    }
    if (ctx.sortOp() != null) {
      return visitSortOp(ctx.sortOp());
    }
    if (ctx.limitOp() != null) {
      return visitLimitOp(ctx.limitOp());
    }
    return visitCountByOp(ctx.countByOp());
  }

  @Override
  public SelectOp visitSelectOp(OkapiLogQlParser.SelectOpContext ctx) {
    return new SelectOp(visitFieldList(ctx.fieldList()));
  }

  @Override
  public RemoveOp visitRemoveOp(OkapiLogQlParser.RemoveOpContext ctx) {
    return new RemoveOp(visitFieldList(ctx.fieldList()));
  }

  @Override
  public SortOp visitSortOp(OkapiLogQlParser.SortOpContext ctx) {
    var direction =
        ctx.sortDirection() == null
            ? PipelineOp.Direction.UNSPECIFIED
            : visitSortDirection(ctx.sortDirection());
    return new SortOp(visitFieldRef(ctx.fieldRef()), direction);
  }

  @Override
  public PipelineOp.Direction visitSortDirection(OkapiLogQlParser.SortDirectionContext ctx) {
    return ctx.ASC() == null ? PipelineOp.Direction.DESC : PipelineOp.Direction.ASC;
  }

  @Override
  public LimitOp visitLimitOp(OkapiLogQlParser.LimitOpContext ctx) {
    return new LimitOp(Integer.parseInt(ctx.INTEGER().getText()));
  }

  @Override
  public CountByOp visitCountByOp(OkapiLogQlParser.CountByOpContext ctx) {
    return new CountByOp(visitFieldList(ctx.fieldList()));
  }

  @Override
  public List<FieldRef> visitFieldList(OkapiLogQlParser.FieldListContext ctx) {
    return ctx.fieldRef().stream().map(this::visitFieldRef).toList();
  }

  @Override
  public FieldRef visitFieldRef(OkapiLogQlParser.FieldRefContext ctx) {
    var path = ctx.fieldPath().IDENTIFIER().stream().map(ParseTree::getText).toList();
    if (ctx.stringLiteral() == null) {
      return new PathFieldRef(path);
    }
    return new MapFieldRef(path, unquote(ctx.stringLiteral().getText()));
  }

  private static ComparisonOp comparisonOp(OkapiLogQlParser.ComparisonOpContext ctx) {
    int type = ctx.getStart().getType();
    return switch (type) {
      case OkapiLogQlLexer.EQ -> ComparisonOp.EQ;
      case OkapiLogQlLexer.NEQ -> ComparisonOp.NEQ;
      case OkapiLogQlLexer.GT -> ComparisonOp.GT;
      case OkapiLogQlLexer.GTE -> ComparisonOp.GTE;
      case OkapiLogQlLexer.LT -> ComparisonOp.LT;
      case OkapiLogQlLexer.LTE -> ComparisonOp.LTE;
      case OkapiLogQlLexer.CONTAINS -> ComparisonOp.CONTAINS;
      case OkapiLogQlLexer.NOT_CONTAINS -> ComparisonOp.NOT_CONTAINS;
      case OkapiLogQlLexer.REGEX -> ComparisonOp.REGEX;
      case OkapiLogQlLexer.NOT_REGEX -> ComparisonOp.NOT_REGEX;
      default ->
          throw new IllegalArgumentException("unknown comparison operator: " + ctx.getText());
    };
  }

  private static String unquote(String text) {
    return text.substring(1, text.length() - 1).translateEscapes();
  }
}

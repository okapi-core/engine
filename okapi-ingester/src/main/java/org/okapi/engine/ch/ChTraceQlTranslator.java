/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.engine.ch;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.okapi.ch.ChSqlEscaper;
import org.okapi.engine.ch.ChQueryModel.*;
import org.okapi.engine.ch.ChTraceQlTranslationBridge.ValueHint;
import org.okapi.exceptions.BadRequestException;
import org.okapi.metrics.ch.ChConstants;
import org.okapi.ql.ast.*;
import org.okapi.rest.traces.OkapiTraceQlResultKind;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChTraceQlTranslator {
  private static final int DEFAULT_LIMIT = ChConstants.TRACE_QUERY_LIMIT;

  private final ChTraceQlTranslationBridge bridge;

  public LogQuery translateTraceQl(LogQueryExpr expr, Long tsStartNs, Long tsEndNs) {
    if (!(expr instanceof QueryLogQueryExpr queryExpr)) {
      throw new BadRequestException("expected query expression");
    }

    var where = new ArrayList<BooleanExpr>();
    if (tsStartNs != null) {
      where.add(
          new BinaryPredicate(
              new ColumnExpr("ts_start_ns", ScalarType.LONG),
              BinaryOperator.GTE,
              new LiteralExpr(tsStartNs, ScalarType.LONG)));
    }
    if (tsEndNs != null) {
      where.add(
          new BinaryPredicate(
              new ColumnExpr("ts_end_ns", ScalarType.LONG),
              BinaryOperator.LTE,
              new LiteralExpr(tsEndNs, ScalarType.LONG)));
    }
    if (queryExpr.getWith() != null) {
      where.add(translateBoolean(queryExpr.getWith()));
    }
    if (queryExpr.getFilter() != null) {
      where.add(translateBoolean(queryExpr.getFilter()));
    }

    var builder =
        LogQuery.builder()
            .table(ChConstants.TBL_SPANS_V1)
            .where(and(where))
            .selectItems(bridge.defaultSpanSelectItems())
            .orderBy(
                List.of(
                    new OrderByItem(
                        new ColumnExpr("ts_start_ns", ScalarType.LONG), SortDirection.DESC)))
            .limit(DEFAULT_LIMIT)
            .traceResultKind(OkapiTraceQlResultKind.SPAN_ROWS)
            .groupBy(List.of());

    applyPipeline(builder, queryExpr.getPipeline());
    return builder.build();
  }

  private void applyPipeline(LogQuery.LogQueryBuilder builder, List<PipelineOp> pipeline) {
    if (pipeline == null) {
      return;
    }
    for (var op : pipeline) {
      switch (op) {
        case SelectOp selectOp -> {
          builder.selectItems(selectItems(selectOp.getFields()));
          builder.traceResultKind(OkapiTraceQlResultKind.TABLE);
        }
        case RemoveOp removeOp -> {
          builder.selectItems(removeItems(removeOp.getFields()));
          builder.traceResultKind(OkapiTraceQlResultKind.TABLE);
        }
        case SortOp sortOp -> builder.orderBy(List.of(orderBy(sortOp)));
        case LimitOp limitOp -> builder.limit(limit(limitOp.getCount()));
        case CountByOp countByOp -> applyCountBy(builder, countByOp);
      }
    }
  }

  private List<SelectItem> selectItems(List<FieldRef> fields) {
    return fields.stream()
        .map(
            field ->
                new SelectItem(
                    bridge.resolve(field, ValueHint.UNKNOWN).getExpression(),
                    bridge.aliasFor(field)))
        .toList();
  }

  private List<SelectItem> removeItems(List<FieldRef> fields) {
    var aliases = fields.stream().map(bridge::aliasFor).toList();
    return bridge.defaultSpanSelectItems().stream()
        .filter(item -> !aliases.contains(item.getAlias()))
        .toList();
  }

  private OrderByItem orderBy(SortOp sortOp) {
    var direction =
        sortOp.getDirection() == PipelineOp.Direction.ASC ? SortDirection.ASC : SortDirection.DESC;
    return new OrderByItem(
        bridge.resolve(sortOp.getField(), ValueHint.UNKNOWN).getExpression(), direction);
  }

  private int limit(int count) {
    if (count <= 0) {
      throw new BadRequestException("limit must be greater than zero");
    }
    return Math.min(count, DEFAULT_LIMIT);
  }

  private void applyCountBy(LogQuery.LogQueryBuilder builder, CountByOp countByOp) {
    var groupBy =
        countByOp.getFields().stream()
            .map(field -> bridge.resolve(field, ValueHint.UNKNOWN).getExpression())
            .toList();
    var selectItems = new ArrayList<SelectItem>();
    for (var field : countByOp.getFields()) {
      selectItems.add(
          new SelectItem(
              bridge.resolve(field, ValueHint.UNKNOWN).getExpression(), bridge.aliasFor(field)));
    }
    selectItems.add(new SelectItem(new RawExpr("count()", ScalarType.LONG), "count"));
    builder
        .selectItems(selectItems)
        .groupBy(groupBy)
        .orderBy(
            List.of(new OrderByItem(new RawExpr("count", ScalarType.LONG), SortDirection.DESC)))
        .traceResultKind(OkapiTraceQlResultKind.COUNT_BY);
  }

  private BooleanExpr translateBoolean(LogQueryExpr expr) {
    return switch (expr) {
      case AndLogQueryExpr andExpr ->
          and(andExpr.getChildren().stream().map(this::translateBoolean).toList());
      case OrLogQueryExpr orExpr ->
          new OrExpr(orExpr.getChildren().stream().map(this::translateBoolean).toList());
      case NotLogQueryExpr notExpr -> new NotExpr(translateBoolean(notExpr.getInner()));
      case ComparisonLogQueryExpr comparison -> translateComparison(comparison);
      case FreeTextLogQueryExpr freeText -> translateFreeText(freeText);
      case QueryLogQueryExpr query -> {
        var children = new ArrayList<BooleanExpr>();
        if (query.getWith() != null) {
          children.add(translateBoolean(query.getWith()));
        }
        if (query.getFilter() != null) {
          children.add(translateBoolean(query.getFilter()));
        }
        yield and(children);
      }
    };
  }

  private BooleanExpr translateFreeText(FreeTextLogQueryExpr expr) {
    var needle = literal(expr.getValue(), ValueHint.STRING);
    return new OrExpr(
        List.of(
            contains(new ColumnExpr("service_name", ScalarType.STRING), needle, false),
            contains(new ColumnExpr("trace_id", ScalarType.STRING), needle, false),
            contains(new ColumnExpr("span_id", ScalarType.STRING), needle, false),
            contains(new ColumnExpr("db_query_text", ScalarType.STRING), needle, false),
            contains(new ColumnExpr("rpc_method", ScalarType.STRING), needle, false)));
  }

  private BooleanExpr translateComparison(ComparisonLogQueryExpr expr) {
    if (expr.getOp() == ComparisonOp.EXISTS || expr.getOp() == ComparisonOp.NOT_EXISTS) {
      var exists = bridge.exists(expr.getField());
      return expr.getOp() == ComparisonOp.EXISTS ? exists : new NotExpr(exists);
    }

    var hint = valueHint(expr.getValues());
    var left = bridge.resolve(expr.getField(), hint).getExpression();
    return switch (expr.getOp()) {
      case EQ, NEQ, GT, GTE, LT, LTE ->
          new BinaryPredicate(left, binaryOperator(expr.getOp()), literal(singleValue(expr), hint));
      case CONTAINS -> contains(left, literal(singleValue(expr), ValueHint.STRING), false);
      case NOT_CONTAINS -> contains(left, literal(singleValue(expr), ValueHint.STRING), true);
      case REGEX -> regex(left, singleString(expr), false);
      case NOT_REGEX -> regex(left, singleString(expr), true);
      case IN -> in(left, expr.getValues(), hint, false);
      case NOT_IN -> in(left, expr.getValues(), hint, true);
      case EXISTS, NOT_EXISTS -> throw new IllegalStateException("handled above");
    };
  }

  private BooleanExpr contains(ScalarExpr left, ScalarExpr right, boolean negated) {
    var expr = "positionCaseInsensitive(" + scalarSql(left) + ", " + scalarSql(right) + ")";
    return new RawPredicate(expr + (negated ? " = 0" : " > 0"));
  }

  private BooleanExpr regex(ScalarExpr left, String pattern, boolean negated) {
    var expr = "match(" + scalarSql(left) + ", '" + ChSqlEscaper.escapeLiteral(pattern) + "')";
    return new RawPredicate(negated ? "NOT " + expr : expr);
  }

  private BooleanExpr in(
      ScalarExpr left, List<LiteralValue> values, ValueHint hint, boolean negated) {
    var rendered = values.stream().map(value -> scalarSql(literal(value, hint))).toList();
    return new RawPredicate(
        scalarSql(left) + (negated ? " NOT IN (" : " IN (") + String.join(", ", rendered) + ")");
  }

  private String scalarSql(ScalarExpr expr) {
    return new ChExprQueryWriter().renderScalar(expr);
  }

  private ScalarExpr literal(LiteralValue value, ValueHint hint) {
    return switch (value) {
      case StringValue stringValue -> new LiteralExpr(stringValue.getValue(), ScalarType.STRING);
      case IdentifierValue identifierValue ->
          new LiteralExpr(
              normalizeIdentifierLiteral(identifierValue.getValue(), hint), ScalarType.STRING);
      case IntegerValue integerValue -> new LiteralExpr(integerValue.getValue(), ScalarType.LONG);
      case DecimalValue decimalValue -> new LiteralExpr(decimalValue.getValue(), ScalarType.DOUBLE);
      case TimestampValue timestampValue ->
          new RawExpr(timestampToNs(timestampValue.getValue()), ScalarType.LONG);
      case DurationValue durationValue ->
          new LiteralExpr(durationToNanos(durationValue.getValue()), ScalarType.LONG);
      case NowValue nowValue -> new RawExpr(nowToNs(nowValue), ScalarType.LONG);
    };
  }

  private String normalizeIdentifierLiteral(String value, ValueHint hint) {
    if (hint == ValueHint.STRING && isSpanStatus(value)) {
      return value.toUpperCase();
    }
    return value;
  }

  private boolean isSpanStatus(String value) {
    return List.of("ok", "error", "unk").contains(value.toLowerCase());
  }

  private String timestampToNs(String value) {
    return "toUnixTimestamp64Nano(parseDateTime64BestEffort('"
        + ChSqlEscaper.escapeLiteral(value)
        + "'))";
  }

  private String nowToNs(NowValue value) {
    var expr = "toUnixTimestamp64Nano(now64(9))";
    if (value.getOffset() == null) {
      return expr;
    }
    var nanos = durationToNanos(value.getOffset().getValue());
    return expr + ("-".equals(value.getOffsetSign()) ? " - " : " + ") + nanos;
  }

  private long durationToNanos(String value) {
    var lower = value.toLowerCase();
    var unit = lower.replaceAll("[0-9.]", "");
    var amount = Double.parseDouble(lower.substring(0, lower.length() - unit.length()));
    var multiplier =
        switch (unit) {
          case "ns" -> 1L;
          case "us" -> 1_000L;
          case "ms" -> 1_000_000L;
          case "s" -> 1_000_000_000L;
          case "m" -> 60_000_000_000L;
          case "h" -> 3_600_000_000_000L;
          case "d" -> 86_400_000_000_000L;
          case "w" -> 604_800_000_000_000L;
          default -> throw new BadRequestException("unsupported duration unit: " + unit);
        };
    return (long) (amount * multiplier);
  }

  private ValueHint valueHint(List<LiteralValue> values) {
    if (values == null || values.isEmpty()) {
      return ValueHint.UNKNOWN;
    }
    var first = values.getFirst();
    if (first instanceof IntegerValue
        || first instanceof DecimalValue
        || first instanceof DurationValue
        || first instanceof TimestampValue
        || first instanceof NowValue) {
      return ValueHint.NUMBER;
    }
    return ValueHint.STRING;
  }

  private LiteralValue singleValue(ComparisonLogQueryExpr expr) {
    if (expr.getValues() == null || expr.getValues().size() != 1) {
      throw new BadRequestException(expr.getOp() + " requires exactly one value");
    }
    return expr.getValues().getFirst();
  }

  private String singleString(ComparisonLogQueryExpr expr) {
    var value = singleValue(expr);
    return switch (value) {
      case StringValue stringValue -> stringValue.getValue();
      case IdentifierValue identifierValue -> identifierValue.getValue();
      default -> throw new BadRequestException(expr.getOp() + " requires a string value");
    };
  }

  private BinaryOperator binaryOperator(ComparisonOp op) {
    return switch (op) {
      case EQ -> BinaryOperator.EQ;
      case NEQ -> BinaryOperator.NEQ;
      case GT -> BinaryOperator.GT;
      case GTE -> BinaryOperator.GTE;
      case LT -> BinaryOperator.LT;
      case LTE -> BinaryOperator.LTE;
      default -> throw new IllegalArgumentException("not a binary comparison: " + op);
    };
  }

  private BooleanExpr and(List<BooleanExpr> children) {
    var compact = children.stream().filter(Objects::nonNull).toList();
    if (compact.isEmpty()) {
      return new RawPredicate("1=1");
    }
    if (compact.size() == 1) {
      return compact.getFirst();
    }
    return new AndExpr(compact);
  }
}

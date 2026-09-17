/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.engine.ch;

import java.util.List;
import lombok.Builder;
import lombok.Value;
import org.okapi.rest.logs.OkapiLogQlResultKind;
import org.okapi.rest.traces.OkapiTraceQlResultKind;

public final class ChQueryModel {
  private ChQueryModel() {}

  public enum ScalarType {
    STRING,
    INTEGER,
    LONG,
    DOUBLE,
    NUMBER,
    BOOLEAN
  }

  public enum SortDirection {
    ASC,
    DESC
  }

  public enum BinaryOperator {
    EQ,
    NEQ,
    GT,
    GTE,
    LT,
    LTE
  }

  @Value
  @Builder
  public static class LogQuery implements ChQueryNode {
    String table;
    List<SelectItem> selectItems;
    BooleanExpr where;
    List<ScalarExpr> groupBy;
    List<OrderByItem> orderBy;
    Integer limit;
    OkapiLogQlResultKind resultKind;
    OkapiTraceQlResultKind traceResultKind;

    @Override
    public List<ChQueryNode> getChildren() {
      return List.of();
    }
  }

  public sealed interface ScalarExpr extends ChQueryNode
      permits ColumnExpr, LiteralExpr, FunctionExpr, RawExpr {
    ScalarType getType();
  }

  public sealed interface BooleanExpr extends ChQueryNode
      permits AndExpr, OrExpr, NotExpr, BinaryPredicate, RawPredicate {}

  @Value
  public static class ColumnExpr implements ScalarExpr {
    String expression;
    ScalarType type;

    @Override
    public List<ChQueryNode> getChildren() {
      return List.of();
    }
  }

  @Value
  public static class RawExpr implements ScalarExpr {
    String expression;
    ScalarType type;

    @Override
    public List<ChQueryNode> getChildren() {
      return List.of();
    }
  }

  @Value
  public static class LiteralExpr implements ScalarExpr {
    Object value;
    ScalarType type;

    @Override
    public List<ChQueryNode> getChildren() {
      return List.of();
    }
  }

  @Value
  public static class FunctionExpr implements ScalarExpr {
    String functionName;
    List<ScalarExpr> args;
    ScalarType type;

    @Override
    public List<ChQueryNode> getChildren() {
      return args.stream().map(ChQueryNode.class::cast).toList();
    }
  }

  @Value
  public static class AndExpr implements BooleanExpr {
    List<BooleanExpr> operands;

    @Override
    public List<ChQueryNode> getChildren() {
      return operands.stream().map(ChQueryNode.class::cast).toList();
    }
  }

  @Value
  public static class OrExpr implements BooleanExpr {
    List<BooleanExpr> operands;

    @Override
    public List<ChQueryNode> getChildren() {
      return operands.stream().map(ChQueryNode.class::cast).toList();
    }
  }

  @Value
  public static class NotExpr implements BooleanExpr {
    BooleanExpr inner;

    @Override
    public List<ChQueryNode> getChildren() {
      return List.of(inner);
    }
  }

  @Value
  public static class BinaryPredicate implements BooleanExpr {
    ScalarExpr left;
    BinaryOperator operator;
    ScalarExpr right;

    @Override
    public List<ChQueryNode> getChildren() {
      return List.of(left, right);
    }
  }

  @Value
  public static class RawPredicate implements BooleanExpr {
    String expression;

    @Override
    public List<ChQueryNode> getChildren() {
      return List.of();
    }
  }

  @Value
  public static class SelectItem implements ChQueryNode {
    ScalarExpr expr;
    String alias;

    @Override
    public List<ChQueryNode> getChildren() {
      return List.of(expr);
    }
  }

  @Value
  public static class OrderByItem implements ChQueryNode {
    ScalarExpr expr;
    SortDirection direction;

    @Override
    public List<ChQueryNode> getChildren() {
      return List.of(expr);
    }
  }
}

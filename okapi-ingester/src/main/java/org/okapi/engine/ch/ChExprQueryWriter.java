/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.engine.ch;

import java.util.StringJoiner;
import org.okapi.ch.ChSqlEscaper;
import org.okapi.engine.ch.ChQueryModel.AndExpr;
import org.okapi.engine.ch.ChQueryModel.BinaryPredicate;
import org.okapi.engine.ch.ChQueryModel.ColumnExpr;
import org.okapi.engine.ch.ChQueryModel.FunctionExpr;
import org.okapi.engine.ch.ChQueryModel.LiteralExpr;
import org.okapi.engine.ch.ChQueryModel.LogQuery;
import org.okapi.engine.ch.ChQueryModel.NotExpr;
import org.okapi.engine.ch.ChQueryModel.OrExpr;
import org.okapi.engine.ch.ChQueryModel.RawExpr;
import org.okapi.engine.ch.ChQueryModel.RawPredicate;
import org.okapi.engine.ch.ChQueryModel.ScalarExpr;

public class ChExprQueryWriter {
  private final StringBuilder queryBuilder = new StringBuilder();

  public void visit(LogQuery queryNode) {
    queryBuilder.append("SELECT ");
    writeSelect(queryNode);
    queryBuilder.append(" FROM ").append(queryNode.getTable());
    if (queryNode.getWhere() != null) {
      queryBuilder.append(" WHERE ");
      writeBoolean(queryNode.getWhere());
    }
    if (queryNode.getGroupBy() != null && !queryNode.getGroupBy().isEmpty()) {
      queryBuilder.append(" GROUP BY ");
      writeScalarList(queryNode.getGroupBy());
    }
    if (queryNode.getOrderBy() != null && !queryNode.getOrderBy().isEmpty()) {
      queryBuilder.append(" ORDER BY ");
      var joiner = new StringJoiner(", ");
      for (var item : queryNode.getOrderBy()) {
        joiner.add(renderScalar(item.getExpr()) + " " + item.getDirection().name());
      }
      queryBuilder.append(joiner);
    }
    if (queryNode.getLimit() != null) {
      queryBuilder.append(" LIMIT ").append(queryNode.getLimit());
    }
  }

  public String getQuery() {
    return queryBuilder.toString();
  }

  private void writeSelect(LogQuery queryNode) {
    var joiner = new StringJoiner(", ");
    for (var item : queryNode.getSelectItems()) {
      var rendered = renderScalar(item.getExpr());
      if (item.getAlias() != null && !item.getAlias().isBlank()) {
        rendered = rendered + " AS " + item.getAlias();
      }
      joiner.add(rendered);
    }
    queryBuilder.append(joiner);
  }

  private void writeScalarList(Iterable<ScalarExpr> exprs) {
    var joiner = new StringJoiner(", ");
    for (var expr : exprs) {
      joiner.add(renderScalar(expr));
    }
    queryBuilder.append(joiner);
  }

  String renderScalar(ScalarExpr expr) {
    return switch (expr) {
      case ColumnExpr column -> column.getExpression();
      case RawExpr raw -> raw.getExpression();
      case LiteralExpr literal -> renderLiteral(literal);
      case FunctionExpr fn -> renderFunction(fn);
    };
  }

  private String renderFunction(FunctionExpr fn) {
    var joiner = new StringJoiner(", ");
    for (var arg : fn.getArgs()) {
      joiner.add(renderScalar(arg));
    }
    return fn.getFunctionName() + "(" + joiner + ")";
  }

  private String renderLiteral(LiteralExpr literal) {
    var value = literal.getValue();
    if (value == null) {
      return "NULL";
    }
    if (value instanceof Number || value instanceof Boolean) {
      return value.toString();
    }
    return "'" + ChSqlEscaper.escapeLiteral(value.toString()) + "'";
  }

  private void writeBoolean(ChQueryModel.BooleanExpr expr) {
    switch (expr) {
      case AndExpr and -> writeAnd(and);
      case OrExpr or -> writeOr(or);
      case NotExpr not -> {
        queryBuilder.append("NOT (");
        writeBoolean(not.getInner());
        queryBuilder.append(")");
      }
      case BinaryPredicate predicate -> writeBinaryPredicate(predicate);
      case RawPredicate raw -> queryBuilder.append(raw.getExpression());
    }
  }

  private void writeAnd(AndExpr expr) {
    writeBooleanList(expr.getOperands(), " AND ");
  }

  private void writeOr(OrExpr expr) {
    writeBooleanList(expr.getOperands(), " OR ");
  }

  private void writeBooleanList(Iterable<? extends ChQueryModel.BooleanExpr> children, String op) {
    var first = true;
    queryBuilder.append("(");
    for (var child : children) {
      if (!first) {
        queryBuilder.append(op);
      }
      writeBoolean(child);
      first = false;
    }
    queryBuilder.append(")");
  }

  private void writeBinaryPredicate(BinaryPredicate predicate) {
    queryBuilder
        .append(renderScalar(predicate.getLeft()))
        .append(" ")
        .append(renderOperator(predicate.getOperator()))
        .append(" ")
        .append(renderScalar(predicate.getRight()));
  }

  private String renderOperator(ChQueryModel.BinaryOperator operator) {
    return switch (operator) {
      case EQ -> "=";
      case NEQ -> "!=";
      case GT -> ">";
      case GTE -> ">=";
      case LT -> "<";
      case LTE -> "<=";
    };
  }
}

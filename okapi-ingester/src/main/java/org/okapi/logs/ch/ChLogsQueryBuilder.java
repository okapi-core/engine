/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.logs.ch;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.okapi.ch.ChSqlEscaper;
import org.okapi.rest.common.UNION_TYPE;
import org.okapi.rest.common.UnionValue;
import org.okapi.rest.logs.ChLogFilter;
import org.okapi.rest.logs.ChLogFilterOp;

final class ChLogsQueryBuilder {

  static final Map<String, String> CORE_ATTRIBS =
      Map.of(
          "service.name", "service_name",
          "log.stream", "log_stream",
          "severity.number", "log_level",
          "severity.text", "severity_text",
          "trace.id", "trace_id",
          "span.id", "span_id",
          "body", "body",
          "ts", "ts_ns");

  static List<ChLogFilterClause> buildFilters(List<ChLogFilter> filters) {
    if (filters == null || filters.isEmpty()) {
      return List.of();
    }
    var clauses = new ArrayList<ChLogFilterClause>();
    for (var filter : filters) {
      if (filter == null) {
        continue;
      }
      clauses.add(buildFilter(filter));
    }
    return clauses;
  }

  static String buildWhereClause(Long tsStartNs, Long tsEndNs, List<ChLogFilter> filters) {
    var clauses = new ArrayList<String>();
    clauses.add("1=1");
    if (tsStartNs != null) {
      clauses.add("ts_ns >= " + tsStartNs);
    }
    if (tsEndNs != null) {
      clauses.add("ts_ns <= " + tsEndNs);
    }
    for (var filter : buildFilters(filters)) {
      clauses.add(filter.getSql());
    }
    return String.join(" AND ", clauses);
  }

  static String severityExpr() {
    return """
        multiIf(
          log_level >= 21, 'FATAL',
          log_level >= 17, 'ERROR',
          log_level >= 13, 'WARN',
          log_level >= 9, 'INFO',
          log_level >= 5, 'DEBUG',
          log_level >= 1, 'TRACE',
          'UNSPECIFIED')
        """;
  }

  private static ChLogFilterClause buildFilter(ChLogFilter filter) {
    var op = filter.getOp();
    var expression = resolveExpression(filter.getKey(), filter.getValue());
    var existsExpression = resolveExistsExpression(filter.getKey(), filter.getValue());
    return ChLogFilterClause.builder()
        .sql(toSql(expression, existsExpression, op, filter.getValue()))
        .build();
  }

  static String resolveExpression(String key, UnionValue value) {
    var matchingCoreCol = coreColumn(key);
    if (matchingCoreCol.isPresent()) {
      return matchingCoreCol.get();
    }
    var bucket = ChLogsAttributeBucketer.bucketForKey(key);
    if (value == null || value.getType() == null) {
      var escapedKey = ChSqlEscaper.escapeLiteral(key);
      return "(mapContains(attribs_str_"
          + bucket
          + ", '"
          + escapedKey
          + "') OR mapContains(attribs_number_"
          + bucket
          + ", '"
          + escapedKey
          + "'))";
    }
    var mapPrefix = isNumeric(value.getType()) ? "number_" : "str_";
    return "attribs_" + mapPrefix + bucket + "['" + ChSqlEscaper.escapeLiteral(key) + "']";
  }

  static String resolveExistsExpression(String key, UnionValue value) {
    var matchingCoreCol = coreColumn(key);
    if (matchingCoreCol.isPresent()) {
      return "notEmpty(toString(" + matchingCoreCol.get() + "))";
    }
    var bucket = ChLogsAttributeBucketer.bucketForKey(key);
    if (value == null || value.getType() == null) {
      var escapedKey = ChSqlEscaper.escapeLiteral(key);
      return "(mapContains(attribs_str_"
          + bucket
          + ", '"
          + escapedKey
          + "') OR mapContains(attribs_number_"
          + bucket
          + ", '"
          + escapedKey
          + "'))";
    }
    var type = value.getType();
    var mapPrefix = isNumeric(type) ? "number_" : "str_";
    return "mapContains("
        + "attribs_"
        + mapPrefix
        + bucket
        + ", '"
        + ChSqlEscaper.escapeLiteral(key)
        + "')";
  }

  private static String toSql(
      String expression, String existsExpression, ChLogFilterOp op, UnionValue value) {
    if (op == ChLogFilterOp.EXISTS) {
      return existsExpression;
    }
    if (op == ChLogFilterOp.NOT_EXISTS) {
      return "NOT " + existsExpression;
    }
    var literal = literal(value);
    return switch (op) {
      case EQ -> expression + " = " + literal;
      case NEQ -> expression + " != " + literal;
      case CONTAINS -> "positionCaseInsensitive(" + expression + ", " + literal + ") > 0";
      case REGEX -> "match(" + expression + ", " + literal + ")";
      case PREFIX -> "startsWith(" + expression + ", " + literal + ")";
      case GT -> expression + " > " + literal;
      case GTE -> expression + " >= " + literal;
      case LT -> expression + " < " + literal;
      case LTE -> expression + " <= " + literal;
      case EXISTS, NOT_EXISTS -> throw new IllegalStateException("unexpected existence operation");
    };
  }

  private static String literal(UnionValue value) {
    return switch (value.getType()) {
      case STRING ->
          "'" + ChSqlEscaper.escapeLiteral(require(value.getStringValue(), "string value")) + "'";
      case BOOLEAN -> require(value.getBooleanValue(), "boolean value") ? "1" : "0";
      case DOUBLE -> require(value.getDoubleValue(), "double value").toString();
      case INTEGER -> require(value.getIntegerValue(), "integer value").toString();
      case LONG -> require(value.getLongValue(), "long value").toString();
    };
  }

  private static <T> T require(T value, String name) {
    return Objects.requireNonNull(value, name + " is required");
  }

  private static boolean isNumeric(UNION_TYPE type) {
    return type == UNION_TYPE.DOUBLE || type == UNION_TYPE.INTEGER || type == UNION_TYPE.LONG;
  }

  private static Optional<String> coreColumn(String key) {
    return Optional.ofNullable(CORE_ATTRIBS.get(key));
  }
}

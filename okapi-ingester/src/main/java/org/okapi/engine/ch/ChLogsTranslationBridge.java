/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.engine.ch;

import java.util.List;
import java.util.Locale;
import lombok.Value;
import org.okapi.ch.ChSqlEscaper;
import org.okapi.engine.ch.ChQueryModel.BinaryOperator;
import org.okapi.engine.ch.ChQueryModel.BinaryPredicate;
import org.okapi.engine.ch.ChQueryModel.BooleanExpr;
import org.okapi.engine.ch.ChQueryModel.ColumnExpr;
import org.okapi.engine.ch.ChQueryModel.RawExpr;
import org.okapi.engine.ch.ChQueryModel.RawPredicate;
import org.okapi.engine.ch.ChQueryModel.ScalarExpr;
import org.okapi.engine.ch.ChQueryModel.ScalarType;
import org.okapi.logs.ch.ChLogsAttributeBucketer;
import org.okapi.ql.ast.FieldRef;
import org.okapi.ql.ast.MapFieldRef;
import org.okapi.ql.ast.PathFieldRef;
import org.springframework.stereotype.Component;

@Component
public class ChLogsTranslationBridge {
  public FieldMapping resolve(FieldRef fieldRef, ValueHint hint) {
    if (fieldRef instanceof MapFieldRef mapFieldRef) {
      return resolveMapField(mapFieldRef, hint);
    }
    if (fieldRef instanceof PathFieldRef pathFieldRef) {
      return resolvePathField(pathFieldRef);
    }
    throw new IllegalArgumentException("unsupported field ref: " + fieldRef);
  }

  public BooleanExpr exists(FieldRef fieldRef) {
    if (fieldRef instanceof MapFieldRef mapFieldRef) {
      var stringMapping = resolveMapField(mapFieldRef, ValueHint.STRING);
      var numberMapping = resolveMapField(mapFieldRef, ValueHint.NUMBER);
      return new RawPredicate(
          "mapContains("
              + stringMapping.getContainerExpression()
              + ", '"
              + ChSqlEscaper.escapeLiteral(mapFieldRef.getKey())
              + "') OR mapContains("
              + numberMapping.getContainerExpression()
              + ", '"
              + ChSqlEscaper.escapeLiteral(mapFieldRef.getKey())
              + "')");
    }
    var mapping = resolve(fieldRef, ValueHint.UNKNOWN);
    if (mapping.getType() == ScalarType.NUMBER) {
      return new RawPredicate("1=1");
    }
    return new BinaryPredicate(
        new RawExpr(
            "notEmpty(" + scalarExpression(mapping.getExpression()) + ")", ScalarType.BOOLEAN),
        BinaryOperator.EQ,
        new ChQueryModel.LiteralExpr(true, ScalarType.BOOLEAN));
  }

  public List<ChQueryModel.SelectItem> defaultLogSelectItems() {
    return List.of(
        select("ts_ns", "ts_ns", ScalarType.NUMBER),
        select("log_stream", "log_stream", ScalarType.STRING),
        select("service_name", "service_name", ScalarType.STRING),
        select("log_level", "log_level", ScalarType.NUMBER),
        select("body", "body", ScalarType.STRING));
  }

  public String aliasFor(FieldRef fieldRef) {
    if (fieldRef instanceof MapFieldRef mapFieldRef) {
      return sanitizeAlias(mapFieldRef.getKey());
    }
    return sanitizeAlias(String.join("_", fieldRef.getPath()));
  }

  private ChQueryModel.SelectItem select(String column, String alias, ScalarType type) {
    return new ChQueryModel.SelectItem(new ColumnExpr(column, type), alias);
  }

  private FieldMapping resolvePathField(PathFieldRef fieldRef) {
    var path = fieldRef.getPath();
    if (path.size() != 1) {
      return resolveAttribute(String.join(".", path), AttributeScope.RECORD, ValueHint.STRING);
    }
    return switch (path.getFirst().toLowerCase(Locale.ROOT)) {
      case "ts", "time", "timestamp" ->
          new FieldMapping(new ColumnExpr("ts_ns", ScalarType.NUMBER), "ts_ns", ScalarType.NUMBER);
      case "stream", "log_stream" ->
          new FieldMapping(
              new ColumnExpr("log_stream", ScalarType.STRING), "log_stream", ScalarType.STRING);
      case "service", "service_name" ->
          new FieldMapping(
              new ColumnExpr("service_name", ScalarType.STRING), "service_name", ScalarType.STRING);
      case "level", "log_level" ->
          new FieldMapping(
              new ColumnExpr("log_level", ScalarType.NUMBER), "log_level", ScalarType.NUMBER);
      case "severity", "severity_text" ->
          new FieldMapping(
              new ColumnExpr("severity_text", ScalarType.STRING),
              "severity_text",
              ScalarType.STRING);
      case "message", "body", "content" ->
          new FieldMapping(new ColumnExpr("body", ScalarType.STRING), "body", ScalarType.STRING);
      case "trace", "trace_id" ->
          new FieldMapping(
              new ColumnExpr("trace_id", ScalarType.STRING), "trace_id", ScalarType.STRING);
      case "span", "span_id" ->
          new FieldMapping(
              new ColumnExpr("span_id", ScalarType.STRING), "span_id", ScalarType.STRING);
      default -> resolveAttribute(path.getFirst(), AttributeScope.RECORD, ValueHint.STRING);
    };
  }

  private FieldMapping resolveMapField(MapFieldRef fieldRef, ValueHint hint) {
    var path = fieldRef.getPath().stream().map(part -> part.toLowerCase(Locale.ROOT)).toList();
    var scope =
        path.contains("resource") || path.contains("labels")
            ? AttributeScope.RESOURCE
            : AttributeScope.RECORD;
    if (hint == ValueHint.UNKNOWN) {
      return resolveAnyTypedAttribute(fieldRef.getKey(), scope);
    }
    return resolveAttribute(fieldRef.getKey(), scope, hint);
  }

  private FieldMapping resolveAnyTypedAttribute(String key, AttributeScope scope) {
    var stringMapping = resolveAttribute(key, scope, ValueHint.STRING);
    var numberMapping = resolveAttribute(key, scope, ValueHint.NUMBER);
    var escapedKey = ChSqlEscaper.escapeLiteral(key);
    var expr =
        "multiIf(mapContains("
            + stringMapping.getContainerExpression()
            + ", '"
            + escapedKey
            + "'), "
            + scalarExpression(stringMapping.getExpression())
            + ", mapContains("
            + numberMapping.getContainerExpression()
            + ", '"
            + escapedKey
            + "'), toString("
            + scalarExpression(numberMapping.getExpression())
            + "), '')";
    return new FieldMapping(new RawExpr(expr, ScalarType.STRING), null, ScalarType.STRING);
  }

  private String scalarExpression(ScalarExpr expr) {
    if (expr instanceof ColumnExpr columnExpr) {
      return columnExpr.getExpression();
    }
    if (expr instanceof RawExpr rawExpr) {
      return rawExpr.getExpression();
    }
    throw new IllegalArgumentException("field mapping must be backed by SQL expression");
  }

  private FieldMapping resolveAttribute(String key, AttributeScope scope, ValueHint hint) {
    var type = hint == ValueHint.NUMBER ? ScalarType.NUMBER : ScalarType.STRING;
    var bucket = ChLogsAttributeBucketer.bucketForKey(key);
    var prefix =
        scope == AttributeScope.RESOURCE
            ? type == ScalarType.NUMBER ? "resource_attribs_number_" : "resource_attribs_str_"
            : type == ScalarType.NUMBER ? "attribs_number_" : "attribs_str_";
    var container = prefix + bucket;
    var escapedKey = ChSqlEscaper.escapeLiteral(key);
    return new FieldMapping(
        new ColumnExpr(container + "['" + escapedKey + "']", type), container, type);
  }

  private String sanitizeAlias(String alias) {
    var sanitized = alias.replaceAll("[^A-Za-z0-9_]", "_");
    if (sanitized.isBlank()) {
      return "value";
    }
    if (Character.isDigit(sanitized.charAt(0))) {
      return "field_" + sanitized;
    }
    return sanitized;
  }

  private enum AttributeScope {
    RECORD,
    RESOURCE
  }

  public enum ValueHint {
    UNKNOWN,
    STRING,
    NUMBER
  }

  @Value
  public static class FieldMapping {
    ScalarExpr expression;
    String containerExpression;
    ScalarType type;
  }
}

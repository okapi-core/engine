/*
 * Copyright The OkapiCore Authors
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
import org.okapi.exceptions.BadRequestException;
import org.okapi.ql.ast.FieldRef;
import org.okapi.ql.ast.MapFieldRef;
import org.okapi.ql.ast.PathFieldRef;
import org.okapi.traces.ch.ChSpanAttributeBucketer;
import org.springframework.stereotype.Component;

@Component
public class ChTraceQlTranslationBridge {
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
      rejectResourceAttributes(mapFieldRef);
      var stringMapping = resolveAttribute(mapFieldRef.getKey(), ValueHint.STRING);
      var numberMapping = resolveAttribute(mapFieldRef.getKey(), ValueHint.NUMBER);
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
    if (mapping.getType() == ScalarType.INTEGER
        || mapping.getType() == ScalarType.LONG
        || mapping.getType() == ScalarType.DOUBLE
        || mapping.getType() == ScalarType.NUMBER) {
      return new RawPredicate("isNotNull(" + scalarExpression(mapping.getExpression()) + ")");
    }
    return new BinaryPredicate(
        new RawExpr(
            "notEmpty(" + scalarExpression(mapping.getExpression()) + ")", ScalarType.BOOLEAN),
        BinaryOperator.EQ,
        new ChQueryModel.LiteralExpr(true, ScalarType.BOOLEAN));
  }

  public List<ChQueryModel.SelectItem> defaultSpanSelectItems() {
    return List.of(
        select("ts_start_ns", "ts_start_ns", ScalarType.LONG),
        select("ts_end_ns", "ts_end_ns", ScalarType.LONG),
        new ChQueryModel.SelectItem(
            new RawExpr("(ts_end_ns - ts_start_ns)", ScalarType.LONG), "duration_ns"),
        select("trace_id", "trace_id", ScalarType.STRING),
        select("span_id", "span_id", ScalarType.STRING),
        select("parent_span_id", "parent_span_id", ScalarType.STRING),
        select("span_status", "span_status", ScalarType.STRING),
        select("kind", "kind", ScalarType.STRING),
        select("service_name", "service_name", ScalarType.STRING),
        select("service_peer_name", "service_peer_name", ScalarType.STRING),
        select("http_method", "http_method", ScalarType.STRING),
        select("http_status_code", "http_status_code", ScalarType.INTEGER),
        select("http_host", "http_host", ScalarType.STRING),
        select("db_system_name", "db_system_name", ScalarType.STRING),
        select("db_operation_name", "db_operation_name", ScalarType.STRING),
        select("rpc_method", "rpc_method", ScalarType.STRING));
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
    var normalized = path.stream().map(part -> part.toLowerCase(Locale.ROOT)).toList();
    var joined = String.join(".", normalized);
    return switch (joined) {
      case "ts", "time", "timestamp", "start", "start_time", "ts_start", "ts_start_ns" ->
          new FieldMapping(
              new ColumnExpr("ts_start_ns", ScalarType.LONG), "ts_start_ns", ScalarType.LONG);
      case "end", "end_time", "ts_end", "ts_end_ns" ->
          new FieldMapping(
              new ColumnExpr("ts_end_ns", ScalarType.LONG), "ts_end_ns", ScalarType.LONG);
      case "duration", "dur", "duration_ns" ->
          new FieldMapping(
              new RawExpr("(ts_end_ns - ts_start_ns)", ScalarType.LONG), null, ScalarType.LONG);
      case "trace", "trace_id" ->
          new FieldMapping(
              new ColumnExpr("trace_id", ScalarType.STRING), "trace_id", ScalarType.STRING);
      case "span", "span_id" ->
          new FieldMapping(
              new ColumnExpr("span_id", ScalarType.STRING), "span_id", ScalarType.STRING);
      case "parent", "parent_span_id" ->
          new FieldMapping(
              new ColumnExpr("parent_span_id", ScalarType.STRING),
              "parent_span_id",
              ScalarType.STRING);
      case "status", "span_status" ->
          new FieldMapping(
              new ColumnExpr("span_status", ScalarType.STRING), "span_status", ScalarType.STRING);
      case "kind" ->
          new FieldMapping(new ColumnExpr("kind", ScalarType.STRING), "kind", ScalarType.STRING);
      case "kind_string" ->
          new FieldMapping(
              new ColumnExpr("kind_string", ScalarType.STRING), "kind_string", ScalarType.STRING);
      case "service", "service_name" ->
          new FieldMapping(
              new ColumnExpr("service_name", ScalarType.STRING), "service_name", ScalarType.STRING);
      case "peer", "service_peer", "peer_service", "service_peer_name" ->
          new FieldMapping(
              new ColumnExpr("service_peer_name", ScalarType.STRING),
              "service_peer_name",
              ScalarType.STRING);
      case "http.method", "http_method", "http.request.method" ->
          new FieldMapping(
              new ColumnExpr("http_method", ScalarType.STRING), "http_method", ScalarType.STRING);
      case "http.status_code", "http_status_code", "http.response.status_code", "status_code" ->
          new FieldMapping(
              new ColumnExpr("http_status_code", ScalarType.INTEGER),
              "http_status_code",
              ScalarType.INTEGER);
      case "http.host", "http_host", "http.request.header.host" ->
          new FieldMapping(
              new ColumnExpr("http_host", ScalarType.STRING), "http_host", ScalarType.STRING);
      case "http.origin", "http_origin" ->
          new FieldMapping(
              new ColumnExpr("http_origin", ScalarType.STRING), "http_origin", ScalarType.STRING);
      case "db.system", "db_system", "db_system_name" ->
          new FieldMapping(
              new ColumnExpr("db_system_name", ScalarType.STRING),
              "db_system_name",
              ScalarType.STRING);
      case "db.collection", "db_collection", "db.collection.name", "db_collection_name" ->
          new FieldMapping(
              new ColumnExpr("db_collection_name", ScalarType.STRING),
              "db_collection_name",
              ScalarType.STRING);
      case "db.namespace", "db_namespace" ->
          new FieldMapping(
              new ColumnExpr("db_namespace", ScalarType.STRING), "db_namespace", ScalarType.STRING);
      case "db.operation", "db_operation", "db.operation.name", "db_operation_name" ->
          new FieldMapping(
              new ColumnExpr("db_operation_name", ScalarType.STRING),
              "db_operation_name",
              ScalarType.STRING);
      case "db.query", "db_query", "db_query_text" ->
          new FieldMapping(
              new ColumnExpr("db_query_text", ScalarType.STRING),
              "db_query_text",
              ScalarType.STRING);
      case "db.query_summary", "db_query_summary" ->
          new FieldMapping(
              new ColumnExpr("db_query_summary", ScalarType.STRING),
              "db_query_summary",
              ScalarType.STRING);
      case "rpc.method", "rpc_method" ->
          new FieldMapping(
              new ColumnExpr("rpc_method", ScalarType.STRING), "rpc_method", ScalarType.STRING);
      case "rpc.status_code", "rpc_status_code", "rpc_response_status_code" ->
          new FieldMapping(
              new ColumnExpr("rpc_response_status_code", ScalarType.INTEGER),
              "rpc_response_status_code",
              ScalarType.INTEGER);
      case "network.protocol_type", "network_protocol_type" ->
          new FieldMapping(
              new ColumnExpr("network_protocol_type", ScalarType.STRING),
              "network_protocol_type",
              ScalarType.STRING);
      case "network.protocol_version", "network_protocol_version" ->
          new FieldMapping(
              new ColumnExpr("network_protocol_version", ScalarType.STRING),
              "network_protocol_version",
              ScalarType.STRING);
      default -> resolveAttribute(String.join(".", path), ValueHint.STRING);
    };
  }

  private FieldMapping resolveMapField(MapFieldRef fieldRef, ValueHint hint) {
    rejectResourceAttributes(fieldRef);
    if (hint == ValueHint.UNKNOWN) {
      return resolveAnyTypedAttribute(fieldRef.getKey());
    }
    return resolveAttribute(fieldRef.getKey(), hint);
  }

  private void rejectResourceAttributes(MapFieldRef fieldRef) {
    var path = fieldRef.getPath().stream().map(part -> part.toLowerCase(Locale.ROOT)).toList();
    if (path.contains("resource") || path.contains("resources")) {
      throw new BadRequestException("resource attributes are not available in TraceQL yet");
    }
  }

  private FieldMapping resolveAnyTypedAttribute(String key) {
    var stringMapping = resolveAttribute(key, ValueHint.STRING);
    var numberMapping = resolveAttribute(key, ValueHint.NUMBER);
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

  private FieldMapping resolveAttribute(String key, ValueHint hint) {
    var type = hint == ValueHint.NUMBER ? ScalarType.DOUBLE : ScalarType.STRING;
    var bucket = ChSpanAttributeBucketer.bucketForKey(key);
    var prefix = type == ScalarType.DOUBLE ? "attribs_number_" : "attribs_str_";
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

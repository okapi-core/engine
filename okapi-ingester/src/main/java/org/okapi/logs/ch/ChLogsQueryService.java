/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.logs.ch;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.query.GenericRecord;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.okapi.ch.ChSqlEscaper;
import org.okapi.ch.ChTemplateFiles;
import org.okapi.metrics.ch.ChConstants;
import org.okapi.rest.common.UNION_TYPE;
import org.okapi.rest.common.UnionValue;
import org.okapi.rest.logs.*;
import org.okapi.spring.configs.Profiles;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@Profile(Profiles.PROFILE_CH)
public class ChLogsQueryService {
  private final Client client;
  private final ChLogsTemplateEngine templateEngine;

  public ChLogsQueryService(Client client, ChLogsTemplateEngine templateEngine) {
    this.client = client;
    this.templateEngine = templateEngine;
  }

  public ChLogsQueryResponse getLogs(ChLogsQueryRequest request) {
    var template = buildTemplate(request);
    var query =
        StringUtils.normalizeSpace(templateEngine.render(ChTemplateFiles.GET_LOGS, template));
    var records = client.queryAll(query);
    var rows = new ArrayList<ChLogRow>(records.size());
    for (var record : records) {
      rows.add(toRow(record, request));
    }
    return ChLogsQueryResponse.builder().items(rows).build();
  }

  public ChLogsFieldsResponse getFields(ChLogsFieldsRequest request) {
    var limit = request == null || request.getLimit() == null ? 50 : request.getLimit();
    var prefix = request == null ? null : request.getQueryPrefix();
    var selects = new ArrayList<String>();
    selects.addAll(
        fieldSelects(
            prefix,
            request == null ? null : request.getTsStartNanos(),
            request == null ? null : request.getTsEndNanos()));
    var query =
        """
        SELECT name, type, sum(count) AS count
        FROM (
        %s
        )
        GROUP BY name, type
        ORDER BY count DESC, name ASC
        LIMIT %d
        """
            .formatted(String.join(" UNION ALL ", selects), Math.min(limit, 100));
    var records = client.queryAll(StringUtils.normalizeSpace(query));
    var fields = new ArrayList<ChLogFieldSuggestion>(records.size());
    for (var record : records) {
      fields.add(
          ChLogFieldSuggestion.builder()
              .name(record.getString("name"))
              .type(UNION_TYPE.valueOf(record.getString("type")))
              .count(record.getLong("count"))
              .exampleValues(List.of())
              .build());
    }
    return ChLogsFieldsResponse.builder().fields(fields).build();
  }

  public ChLogsFieldValuesResponse getFieldValues(ChLogsFieldValuesRequest request) {
    var type = request.getType();
    var value = UnionValue.builder().type(type).build();
    var expression = ChLogsQueryBuilder.resolveExpression(request.getKey(), value);
    var existsExpression = ChLogsQueryBuilder.resolveExistsExpression(request.getKey(), value);
    var where =
        ChLogsQueryBuilder.buildWhereClause(
            request.getTsStartNanos(), request.getTsEndNanos(), request.getFilters());
    var prefixClause = "";
    if (request.getValuePrefix() != null && !request.getValuePrefix().isEmpty()) {
      prefixClause =
          " AND startsWith(toString(value), '"
              + ChSqlEscaper.escapeLiteral(request.getValuePrefix())
              + "')";
    }
    var limit = request.getLimit() == null ? 50 : Math.min(request.getLimit(), 100);
    var query =
        """
        SELECT %s AS value, count() AS count
        FROM %s
        WHERE %s
          AND %s
          %s
        GROUP BY value
        ORDER BY count DESC, toString(value) ASC
        LIMIT %d
        """
            .formatted(
                expression, ChConstants.TBL_LOGS_V1, where, existsExpression, prefixClause, limit);
    var records = client.queryAll(StringUtils.normalizeSpace(query));
    var values = new ArrayList<ChLogFieldValueSuggestion>(records.size());
    for (var record : records) {
      values.add(
          ChLogFieldValueSuggestion.builder()
              .value(toUnionValue(record.getObject("value"), type))
              .count(record.getLong("count"))
              .build());
    }
    return ChLogsFieldValuesResponse.builder().values(values).build();
  }

  private ChLogsQueryTemplate buildTemplate(ChLogsQueryRequest request) {
    var limit =
        request.getLimit() == null
            ? ChConstants.LOGS_QUERY_LIMIT
            : Math.min(request.getLimit(), ChConstants.LOGS_QUERY_LIMIT);
    var filters = ChLogsQueryBuilder.buildFilters(request.getFilters());
    return ChLogsQueryTemplate.builder()
        .table(ChConstants.TBL_LOGS_V1)
        .tsStartNs(request.getTsStartNanos())
        .tsEndNs(request.getTsEndNanos())
        .filters(filters)
        .limit(Math.min(limit, ChConstants.LOGS_QUERY_LIMIT))
        .build();
  }

  private static ChLogRow toRow(GenericRecord record, ChLogsQueryRequest request) {
    return ChLogRow.builder()
        .tsNanos(record.getLong("ts_ns"))
        .logStream(record.getString("log_stream"))
        .serviceName(record.getString("service_name"))
        .logLevel((int) record.getLong("log_level"))
        .severityText(severityText(record))
        .traceId(record.getString("trace_id"))
        .spanId(record.getString("span_id"))
        .body(record.getString("body"))
        .resourceAttributes(
            collectAttributes(
                record,
                "resource_attribs",
                includeResourceAttributes(request),
                request == null ? null : request.getResourceAttributeKeys()))
        .attributes(
            collectAttributes(
                record,
                "attribs",
                includeAttributes(request),
                request == null ? null : request.getAttributeKeys()))
        .build();
  }

  private static String severityText(GenericRecord record) {
    var severityText = record.getString("severity_text");
    if (StringUtils.isNotBlank(severityText)) {
      return severityText;
    }
    var logLevel = record.getLong("log_level");
    if (logLevel >= 21) {
      return "FATAL";
    }
    if (logLevel >= 17) {
      return "ERROR";
    }
    if (logLevel >= 13) {
      return "WARN";
    }
    if (logLevel >= 9) {
      return "INFO";
    }
    if (logLevel >= 5) {
      return "DEBUG";
    }
    if (logLevel >= 1) {
      return "TRACE";
    }
    return "UNSPECIFIED";
  }

  private static List<String> fieldSelects(String prefix, Long tsStartNanos, Long tsEndNanos) {
    var out = new ArrayList<String>();
    var timeWhere = timeWhere(tsStartNanos, tsEndNanos);
    for (var field : coreFields().entrySet()) {
      if (prefix == null || prefix.isEmpty() || field.getKey().startsWith(prefix)) {
        out.add(
            "SELECT '"
                + field.getKey()
                + "' AS name, '"
                + field.getValue()
                + "' AS type, count() AS count FROM "
                + ChConstants.TBL_LOGS_V1
                + " WHERE "
                + timeWhere);
      }
    }
    for (int i = 0; i < ChLogsAttributeBucketer.BUCKETS; i++) {
      out.add(fieldSelect("attribs_str_" + i, UNION_TYPE.STRING, prefix, timeWhere));
      out.add(fieldSelect("attribs_number_" + i, UNION_TYPE.DOUBLE, prefix, timeWhere));
    }
    return out;
  }

  private static String fieldSelect(
      String column, UNION_TYPE type, String prefix, String timeWhere) {
    var clauses = new ArrayList<String>();
    clauses.add(timeWhere);
    if (prefix != null && !prefix.isEmpty()) {
      clauses.add("startsWith(name, '" + ChSqlEscaper.escapeLiteral(prefix) + "')");
    }
    return """
        SELECT name, '%s' AS type, count() AS count
        FROM %s
        ARRAY JOIN mapKeys(%s) AS name
        WHERE %s
        GROUP BY name
        """
        .formatted(type.name(), ChConstants.TBL_LOGS_V1, column, String.join(" AND ", clauses));
  }

  private static String timeWhere(Long tsStartNanos, Long tsEndNanos) {
    var clauses = new ArrayList<String>();
    clauses.add("1=1");
    if (tsStartNanos != null) {
      clauses.add("ts_ns >= " + tsStartNanos);
    }
    if (tsEndNanos != null) {
      clauses.add("ts_ns <= " + tsEndNanos);
    }
    return String.join(" AND ", clauses);
  }

  private static Map<String, UNION_TYPE> coreFields() {
    return Map.of(
        "service.name", UNION_TYPE.STRING,
        "log.stream", UNION_TYPE.STRING,
        "severity.number", UNION_TYPE.INTEGER,
        "severity.text", UNION_TYPE.STRING,
        "trace.id", UNION_TYPE.STRING,
        "span.id", UNION_TYPE.STRING,
        "body", UNION_TYPE.STRING,
        "ts", UNION_TYPE.LONG);
  }

  private static boolean includeAttributes(ChLogsQueryRequest request) {
    return request == null
        || request.getIncludeAttributes() == null
        || request.getIncludeAttributes();
  }

  private static boolean includeResourceAttributes(ChLogsQueryRequest request) {
    return request == null
        || request.getIncludeResourceAttributes() == null
        || request.getIncludeResourceAttributes();
  }

  private static Map<String, UnionValue> collectAttributes(
      GenericRecord record, String prefix, boolean include, List<String> allowedKeys) {
    if (!include) {
      return null;
    }
    var allowed = allowedKeys == null || allowedKeys.isEmpty() ? null : new HashSet<>(allowedKeys);
    var out = new HashMap<String, UnionValue>();
    for (int i = 0; i < ChLogsAttributeBucketer.BUCKETS; i++) {
      @SuppressWarnings("unchecked")
      var strings = (Map<String, String>) record.getObject(prefix + "_str_" + i);
      if (strings != null) {
        for (var entry : strings.entrySet()) {
          if (allowed == null || allowed.contains(entry.getKey())) {
            out.put(entry.getKey(), UnionValue.stringValue(entry.getValue()));
          }
        }
      }
      @SuppressWarnings("unchecked")
      var numbers = (Map<String, Object>) record.getObject(prefix + "_number_" + i);
      if (numbers != null) {
        for (var entry : numbers.entrySet()) {
          if ((allowed == null || allowed.contains(entry.getKey()))
              && entry.getValue() instanceof Number n) {
            out.put(entry.getKey(), UnionValue.doubleValue(n.doubleValue()));
          }
        }
      }
    }
    return out.isEmpty() ? null : out;
  }

  private static UnionValue toUnionValue(Object raw, UNION_TYPE type) {
    if (raw == null) {
      return UnionValue.emptyValue();
    }
    return switch (type) {
      case STRING -> UnionValue.stringValue(raw.toString());
      case BOOLEAN -> UnionValue.booleanValue(Boolean.valueOf(raw.toString()));
      case DOUBLE ->
          raw instanceof Number n
              ? UnionValue.doubleValue(n.doubleValue())
              : UnionValue.doubleValue(Double.valueOf(raw.toString()));
      case INTEGER ->
          raw instanceof Number n
              ? UnionValue.integerValue(n.intValue())
              : UnionValue.integerValue(Integer.valueOf(raw.toString()));
      case LONG ->
          raw instanceof Number n
              ? UnionValue.longValue(n.longValue())
              : UnionValue.longValue(Long.valueOf(raw.toString()));
    };
  }
}

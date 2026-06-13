/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.logs.ch;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.query.GenericRecord;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.okapi.ch.ChTemplateFiles;
import org.okapi.exceptions.BadRequestException;
import org.okapi.metrics.ch.ChConstants;
import org.okapi.rest.logs.ChLogFilter;
import org.okapi.rest.logs.ChLogFilterField;
import org.okapi.rest.logs.ChLogLevelComparison;
import org.okapi.rest.logs.ChLogRow;
import org.okapi.rest.logs.ChLogStringMatchType;
import org.okapi.rest.logs.ChLogsQueryRequest;
import org.okapi.rest.logs.ChLogsQueryResponse;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ChLogsQueryService {
  private final Client client;
  private final ChLogsTemplateEngine templateEngine;

  public ChLogsQueryService(Client client, ChLogsTemplateEngine templateEngine) {
    this.client = client;
    this.templateEngine = templateEngine;
  }

  public ChLogsQueryResponse getLogs(ChLogsQueryRequest request) {
    var template = buildTemplate(request);
    var query = StringUtils.normalizeSpace(templateEngine.render(ChTemplateFiles.GET_LOGS, template));
    var records = client.queryAll(query);
    var rows = new ArrayList<ChLogRow>(records.size());
    for (var record : records) {
      rows.add(toRow(record));
    }
    return ChLogsQueryResponse.builder().items(rows).build();
  }

  private ChLogsQueryTemplate buildTemplate(ChLogsQueryRequest request) {
    if (request == null) {
      throw new BadRequestException("logs query request is required");
    }
    var limit = request.getLimit() == null ? ChConstants.LOGS_QUERY_LIMIT : request.getLimit();
    if (limit <= 0) {
      throw new BadRequestException("limit must be greater than zero");
    }
    var filters = buildFilters(request.getFilters());
    return ChLogsQueryTemplate.builder()
        .table(ChConstants.TBL_LOGS_V1)
        .tsStartNs(request.getTsStartNanos())
        .tsEndNs(request.getTsEndNanos())
        .filters(filters)
        .limit(Math.min(limit, ChConstants.LOGS_QUERY_LIMIT))
        .build();
  }

  private static List<ChLogFilterClause> buildFilters(List<ChLogFilter> filters) {
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

  private static ChLogFilterClause buildFilter(ChLogFilter filter) {
    if (filter.getField() == null) {
      throw new BadRequestException("filter field is required");
    }
    if (filter.getField() == ChLogFilterField.LOG_LEVEL) {
      return buildLogLevelFilter(filter);
    }
    return buildStringFilter(filter);
  }

  private static ChLogFilterClause buildLogLevelFilter(ChLogFilter filter) {
    if (filter.getLevel() == null) {
      throw new BadRequestException("log level filter requires level");
    }
    var comparison =
        filter.getLevelComparison() == null
            ? ChLogLevelComparison.EQUAL
            : filter.getLevelComparison();
    return ChLogFilterClause.builder()
        .column("log_level")
        .operator(levelOperator(comparison))
        .value(filter.getLevel().toString())
        .numeric(true)
        .build();
  }

  private static ChLogFilterClause buildStringFilter(ChLogFilter filter) {
    if (filter.getValue() == null) {
      throw new BadRequestException("string log filter requires value");
    }
    var matchType =
        filter.getStringMatchType() == null ? ChLogStringMatchType.EXACT : filter.getStringMatchType();
    return ChLogFilterClause.builder()
        .column(stringColumn(filter.getField()))
        .operator("=")
        .value(escapeLiteral(filter.getValue()))
        .regex(matchType == ChLogStringMatchType.REGEX)
        .build();
  }

  private static String levelOperator(ChLogLevelComparison comparison) {
    return switch (comparison) {
      case GREATER -> ">";
      case LESS -> "<";
      case EQUAL -> "=";
    };
  }

  private static String stringColumn(ChLogFilterField field) {
    return switch (field) {
      case LOG_STREAM -> "log_stream";
      case SERVICE_NAME -> "service_name";
      case CONTENT -> "body";
      case LOG_LEVEL -> throw new BadRequestException("log level must use level comparison");
    };
  }

  private static ChLogRow toRow(GenericRecord record) {
    return ChLogRow.builder()
        .tsNanos(record.getLong("ts_ns"))
        .logStream(record.getString("log_stream"))
        .serviceName(record.getString("service_name"))
        .logLevel((int) record.getLong("log_level"))
        .body(record.getString("body"))
        .build();
  }

  private static String escapeLiteral(String value) {
    return value
        .replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
        .replace("\0", "\\0");
  }
}

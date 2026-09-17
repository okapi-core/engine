/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.engine.ch;

import com.clickhouse.client.api.Client;
import io.opentelemetry.proto.collector.logs.v1.ExportLogsServiceRequest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.okapi.engine.api.LogsEngine;
import org.okapi.logs.ch.ChLogsIngester;
import org.okapi.logs.ch.ChLogsQueryService;
import org.okapi.logs.ch.ChLogsSummaryService;
import org.okapi.rest.logs.ChLogsFieldValuesRequest;
import org.okapi.rest.logs.ChLogsFieldValuesResponse;
import org.okapi.rest.logs.ChLogsFieldsRequest;
import org.okapi.rest.logs.ChLogsFieldsResponse;
import org.okapi.rest.logs.LogsQueryRequestV2;
import org.okapi.rest.logs.LogsQueryResponseV2;
import org.okapi.rest.logs.LogsSummaryRequest;
import org.okapi.rest.logs.LogsSummaryResponse;
import org.okapi.rest.logs.OkapiLogQlRequest;
import org.okapi.rest.logs.OkapiLogQlResponse;
import org.okapi.spring.configs.Profiles;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile(Profiles.PROFILE_CH)
@RequiredArgsConstructor
public class ChLogsEngine implements LogsEngine {
  private final ChLogsQueryService queryService;
  private final ChLogsSummaryService summaryService;
  private final ChLogsIngester ingester;
  private final ChLogQlTranslator chLogQlTranslator;
  private final ChLogQlParser parser;
  private final ChQueryGenerator gen;
  private final Client client;

  @Override
  public LogsQueryResponseV2 query(LogsQueryRequestV2 request) {
    return ChLogsApiMappers.toQueryResponse(
        queryService.getLogs(ChLogsApiMappers.toChQueryRequest(request)));
  }

  @Override
  public ChLogsFieldsResponse fields(ChLogsFieldsRequest request) {
    return queryService.getFields(request);
  }

  @Override
  public ChLogsFieldValuesResponse fieldValues(ChLogsFieldValuesRequest request) {
    return queryService.getFieldValues(request);
  }

  @Override
  public OkapiLogQlResponse queryWithLogQl(OkapiLogQlRequest request) {
    var exprNode = parser.parseExpression(request.getLogQl());
    var translated =
        chLogQlTranslator.translateLogQl(
            exprNode, request.getTsStartNanos(), request.getTsEndNanos());
    var query = StringUtils.normalizeSpace(gen.generate(translated));
    var records = client.queryAll(query);
    var rows = new ArrayList<LinkedHashMap<String, Object>>(records.size());
    for (var record : records) {
      rows.add(new LinkedHashMap<>(record.getValues()));
    }
    return OkapiLogQlResponse.builder()
        .kind(translated.getResultKind())
        .rows(new ArrayList<>(rows))
        .build();
  }

  @Override
  public LogsSummaryResponse summary(LogsSummaryRequest request) {
    return ChLogsApiMappers.toSummaryResponse(
        summaryService.getSummary(ChLogsApiMappers.toChSummaryRequest(request)));
  }

  @Override
  public void ingest(ExportLogsServiceRequest request) throws Exception {
    ingester.ingest(request);
  }
}

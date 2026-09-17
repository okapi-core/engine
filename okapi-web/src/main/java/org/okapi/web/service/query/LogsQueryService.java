/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.service.query;

import lombok.RequiredArgsConstructor;
import org.okapi.ingester.client.IngesterClient;
import org.okapi.rest.logs.ChLogsFieldValuesRequest;
import org.okapi.rest.logs.ChLogsFieldValuesResponse;
import org.okapi.rest.logs.ChLogsFieldsRequest;
import org.okapi.rest.logs.ChLogsFieldsResponse;
import org.okapi.rest.logs.ChLogsQueryRequest;
import org.okapi.rest.logs.ChLogsQueryResponse;
import org.okapi.rest.logs.ChLogsSummaryRequest;
import org.okapi.rest.logs.ChLogsSummaryResponse;
import org.okapi.rest.logs.OkapiLogQlRequest;
import org.okapi.rest.logs.OkapiLogQlResponse;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LogsQueryService {
  private final IngesterClient ingesterClient;

  public ChLogsQueryResponse searchLogs(ChLogsQueryRequest request) {
    return ingesterClient.searchLogs(request);
  }

  public ChLogsFieldsResponse getLogsFields(ChLogsFieldsRequest request) {
    return ingesterClient.getLogsFields(request);
  }

  public ChLogsFieldValuesResponse getLogsFieldValues(ChLogsFieldValuesRequest request) {
    return ingesterClient.getLogsFieldValues(request);
  }

  public OkapiLogQlResponse queryLogsQl(OkapiLogQlRequest request) {
    return ingesterClient.queryLogsQl(request);
  }

  public ChLogsSummaryResponse getLogsSummary(ChLogsSummaryRequest request) {
    return ingesterClient.getLogsSummary(request);
  }
}

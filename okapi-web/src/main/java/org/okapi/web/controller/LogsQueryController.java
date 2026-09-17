/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.controller;

import lombok.AllArgsConstructor;
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
import org.okapi.web.service.query.LogsQueryService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/logs")
@AllArgsConstructor
public class LogsQueryController {
  LogsQueryService logsQueryService;

  @PostMapping("/query")
  public ChLogsQueryResponse searchLogs(@RequestBody ChLogsQueryRequest request) {
    return logsQueryService.searchLogs(request);
  }

  @PostMapping("/fields")
  public ChLogsFieldsResponse getLogsFields(@RequestBody ChLogsFieldsRequest request) {
    return logsQueryService.getLogsFields(request);
  }

  @PostMapping("/field-values")
  public ChLogsFieldValuesResponse getLogsFieldValues(
      @RequestBody ChLogsFieldValuesRequest request) {
    return logsQueryService.getLogsFieldValues(request);
  }

  @PostMapping("/query/logql")
  public OkapiLogQlResponse queryLogsQl(@RequestBody OkapiLogQlRequest request) {
    return logsQueryService.queryLogsQl(request);
  }

  @PostMapping("/summary")
  public ChLogsSummaryResponse getLogsSummary(@RequestBody ChLogsSummaryRequest request) {
    return logsQueryService.getLogsSummary(request);
  }
}

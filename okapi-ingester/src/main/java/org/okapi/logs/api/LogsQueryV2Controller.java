/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.logs.api;

import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.okapi.engine.api.LogsEngine;
import org.okapi.rest.logs.ChLogsFieldValuesRequest;
import org.okapi.rest.logs.ChLogsFieldValuesResponse;
import org.okapi.rest.logs.ChLogsFieldsRequest;
import org.okapi.rest.logs.ChLogsFieldsResponse;
import org.okapi.rest.logs.LogsQueryRequestV2;
import org.okapi.rest.logs.LogsQueryResponseV2;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class LogsQueryV2Controller {
  private final LogsEngine logsEngine;

  @PostMapping("/logs/query")
  public LogsQueryResponseV2 getLogs(@Validated @NotNull @RequestBody LogsQueryRequestV2 request)
      throws Exception {
    return logsEngine.query(request);
  }

  @PostMapping("/logs/fields")
  public ChLogsFieldsResponse getFields(
      @Validated @NotNull @RequestBody ChLogsFieldsRequest request) throws Exception {
    return logsEngine.fields(request);
  }

  @PostMapping("/logs/field-values")
  public ChLogsFieldValuesResponse getFieldValues(
      @Validated @NotNull @RequestBody ChLogsFieldValuesRequest request) throws Exception {
    return logsEngine.fieldValues(request);
  }
}

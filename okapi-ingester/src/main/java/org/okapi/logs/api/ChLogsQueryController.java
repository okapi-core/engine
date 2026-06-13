/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.logs.api;

import lombok.RequiredArgsConstructor;
import org.okapi.logs.ch.ChLogsQueryService;
import org.okapi.rest.logs.ChLogsQueryRequest;
import org.okapi.rest.logs.ChLogsQueryResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class ChLogsQueryController {
  private final ChLogsQueryService logsQueryService;

  @PostMapping("/logs/query")
  public ChLogsQueryResponse getLogs(@RequestBody ChLogsQueryRequest request) {
    return logsQueryService.getLogs(request);
  }
}

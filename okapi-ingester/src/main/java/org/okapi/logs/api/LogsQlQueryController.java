/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.logs.api;

import lombok.RequiredArgsConstructor;
import org.okapi.engine.api.LogsEngine;
import org.okapi.rest.logs.OkapiLogQlRequest;
import org.okapi.rest.logs.OkapiLogQlResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class LogsQlQueryController {
  private final LogsEngine logsEngine;

  @PostMapping("/logs/query/logql")
  public OkapiLogQlResponse queryLogsQl(@RequestBody OkapiLogQlRequest request) {
    return logsEngine.queryWithLogQl(request);
  }
}

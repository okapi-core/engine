/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.logs.api;

import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.okapi.engine.api.LogsEngine;
import org.okapi.rest.logs.LogsSummaryRequest;
import org.okapi.rest.logs.LogsSummaryResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class LogsSummaryController {
  private final LogsEngine logsEngine;

  @PostMapping("/logs/summary")
  public LogsSummaryResponse getSummary(@Validated @NotNull @RequestBody LogsSummaryRequest request)
      throws Exception {
    return logsEngine.summary(request);
  }
}

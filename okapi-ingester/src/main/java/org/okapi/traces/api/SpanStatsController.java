/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.traces.api;

import lombok.RequiredArgsConstructor;
import org.okapi.engine.api.TracesEngine;
import org.okapi.exceptions.BadRequestException;
import org.okapi.rest.traces.SpansQueryStatsRequest;
import org.okapi.rest.traces.SpansQueryStatsResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class SpanStatsController {
  private final TracesEngine tracesEngine;

  @PostMapping("/spans/stats")
  public SpansQueryStatsResponse getStats(@RequestBody SpansQueryStatsRequest request)
      throws BadRequestException, Exception {
    return tracesEngine.stats(request);
  }
}

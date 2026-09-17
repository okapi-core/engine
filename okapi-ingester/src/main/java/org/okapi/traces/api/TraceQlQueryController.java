/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.traces.api;

import lombok.RequiredArgsConstructor;
import org.okapi.engine.api.TracesEngine;
import org.okapi.rest.traces.OkapiTraceQlRequest;
import org.okapi.rest.traces.OkapiTraceQlResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class TraceQlQueryController {
  private final TracesEngine tracesEngine;

  @PostMapping("/spans/query/traceql")
  public OkapiTraceQlResponse queryTraceQl(@RequestBody OkapiTraceQlRequest request)
      throws Exception {
    return tracesEngine.queryWithTraceQl(request);
  }
}

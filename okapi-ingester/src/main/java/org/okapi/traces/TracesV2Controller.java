/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.traces;

import lombok.RequiredArgsConstructor;
import org.okapi.engine.api.TracesEngine;
import org.okapi.rest.traces.SpanQueryV2Request;
import org.okapi.rest.traces.SpanQueryV2Response;
import org.okapi.rest.traces.SpanQueryV2SummaryResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class TracesV2Controller {
  private final TracesEngine tracesEngine;

  @PostMapping("/spans/query")
  public SpanQueryV2Response getSpans(@RequestBody SpanQueryV2Request requestV2) throws Exception {
    return tracesEngine.query(requestV2);
  }

  @PostMapping("/spans/query/summary")
  public SpanQueryV2SummaryResponse getSpansSummary(@RequestBody SpanQueryV2Request requestV2)
      throws Exception {
    return tracesEngine.summary(requestV2);
  }
}

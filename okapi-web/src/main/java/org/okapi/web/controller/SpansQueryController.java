/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.controller;

import lombok.AllArgsConstructor;
import org.okapi.rest.traces.OkapiTraceQlRequest;
import org.okapi.rest.traces.OkapiTraceQlResponse;
import org.okapi.rest.traces.SpanAttributeHintsRequest;
import org.okapi.rest.traces.SpanAttributeHintsResponse;
import org.okapi.rest.traces.SpanAttributeValueHintsRequest;
import org.okapi.rest.traces.SpanAttributeValueHintsResponse;
import org.okapi.rest.traces.SpanQueryV2Request;
import org.okapi.rest.traces.SpanQueryV2Response;
import org.okapi.rest.traces.SpansFlameGraphResponse;
import org.okapi.rest.traces.SpansQueryStatsRequest;
import org.okapi.rest.traces.SpansQueryStatsResponse;
import org.okapi.web.service.query.SpansQueryService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/spans")
@AllArgsConstructor
public class SpansQueryController {

  SpansQueryService spansQueryService;

  @PostMapping("/query")
  public SpanQueryV2Response querySpans(@RequestBody SpanQueryV2Request request) {
    return spansQueryService.querySpans(request);
  }

  @PostMapping("/query/traceql")
  public OkapiTraceQlResponse queryTraceQl(@RequestBody OkapiTraceQlRequest request) {
    return spansQueryService.queryTraceQl(request);
  }

  @PostMapping("/flamegraph")
  public SpansFlameGraphResponse queryFlameGraph(@RequestBody SpanQueryV2Request request) {
    return spansQueryService.queryFlameGraph(request);
  }

  @PostMapping("/stats")
  public SpansQueryStatsResponse getSpansStats(@RequestBody SpansQueryStatsRequest request) {
    return spansQueryService.getSpansStats(request);
  }

  @PostMapping("/attributes/hints")
  public SpanAttributeHintsResponse getAttributeHints(
      @RequestBody SpanAttributeHintsRequest request) {
    return spansQueryService.getAttributeHints(request);
  }

  @PostMapping("/attributes/values/hints")
  public SpanAttributeValueHintsResponse getAttributeValueHints(
      @RequestBody SpanAttributeValueHintsRequest request) {
    return spansQueryService.getAttributeValueHints(request);
  }
}

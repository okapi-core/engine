/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.service.query;

import lombok.RequiredArgsConstructor;
import org.okapi.ingester.client.IngesterClient;
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
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SpansQueryService {

  private final IngesterClient ingesterClient;

  public SpanQueryV2Response querySpans(SpanQueryV2Request request) {
    return ingesterClient.querySpans(request);
  }

  public OkapiTraceQlResponse queryTraceQl(OkapiTraceQlRequest request) {
    return ingesterClient.queryTraceQl(request);
  }

  public SpansFlameGraphResponse queryFlameGraph(SpanQueryV2Request request) {
    return ingesterClient.querySpansFlameGraph(request);
  }

  public SpansQueryStatsResponse getSpansStats(SpansQueryStatsRequest request) {
    return ingesterClient.getSpansStats(request);
  }

  public SpanAttributeHintsResponse getAttributeHints(SpanAttributeHintsRequest request) {
    return ingesterClient.getSpanAttributeHints(request);
  }

  public SpanAttributeValueHintsResponse getAttributeValueHints(
      SpanAttributeValueHintsRequest request) {
    return ingesterClient.getSpanAttributeValueHints(request);
  }
}

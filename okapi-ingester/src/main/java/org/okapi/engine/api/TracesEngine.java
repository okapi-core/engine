/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.engine.api;

import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import org.okapi.rest.traces.OkapiTraceQlRequest;
import org.okapi.rest.traces.OkapiTraceQlResponse;
import org.okapi.rest.traces.SpanAttributeHintsRequest;
import org.okapi.rest.traces.SpanAttributeHintsResponse;
import org.okapi.rest.traces.SpanAttributeValueHintsRequest;
import org.okapi.rest.traces.SpanAttributeValueHintsResponse;
import org.okapi.rest.traces.SpanQueryV2Request;
import org.okapi.rest.traces.SpanQueryV2Response;
import org.okapi.rest.traces.SpanQueryV2SummaryResponse;
import org.okapi.rest.traces.SpansFlameGraphResponse;
import org.okapi.rest.traces.SpansQueryStatsRequest;
import org.okapi.rest.traces.SpansQueryStatsResponse;
import org.okapi.rest.traces.red.ListServicesRequest;
import org.okapi.rest.traces.red.ServiceListResponse;
import org.okapi.rest.traces.red.ServiceRedRequest;
import org.okapi.rest.traces.red.ServiceRedResponse;

public interface TracesEngine {
  SpanQueryV2Response query(SpanQueryV2Request request) throws Exception;

  OkapiTraceQlResponse queryWithTraceQl(OkapiTraceQlRequest request) throws Exception;

  SpanQueryV2SummaryResponse summary(SpanQueryV2Request request) throws Exception;

  SpansQueryStatsResponse stats(SpansQueryStatsRequest request) throws Exception;

  ServiceRedResponse reds(ServiceRedRequest request) throws Exception;

  ServiceListResponse services(ListServicesRequest request) throws Exception;

  SpanAttributeHintsResponse attributeHints(SpanAttributeHintsRequest request) throws Exception;

  SpanAttributeValueHintsResponse attributeValueHints(SpanAttributeValueHintsRequest request)
      throws Exception;

  SpansFlameGraphResponse flameGraph(SpanQueryV2Request request) throws Exception;

  void ingest(ExportTraceServiceRequest request) throws Exception;
}

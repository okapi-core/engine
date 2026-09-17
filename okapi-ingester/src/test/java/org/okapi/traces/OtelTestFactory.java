/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.traces;

import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.resource.v1.Resource;
import io.opentelemetry.proto.trace.v1.ResourceSpans;
import io.opentelemetry.proto.trace.v1.ScopeSpans;
import io.opentelemetry.proto.trace.v1.Span;
import io.opentelemetry.proto.trace.v1.Status;
import java.util.List;
import org.okapi.otelshorthand.OtelShortHands;
import org.okapi.timeutils.TimeUtils;

public class OtelTestFactory {
  private int idCounter = 1;

  public Span span(
      String spanName,
      String peerService,
      long startMs,
      long durationMs,
      Status.StatusCode status) {
    var startNs = TimeUtils.millisToNanos(startMs);
    var endNs = TimeUtils.millisToNanos(startMs + durationMs);
    return Span.newBuilder()
        .setTraceId(OtelShortHands.utf8Bytes("trace-" + idCounter++))
        .setSpanId(OtelShortHands.utf8Bytes("span-" + idCounter++))
        .setName(spanName)
        .setKind(Span.SpanKind.SPAN_KIND_SERVER)
        .setStartTimeUnixNano(startNs)
        .setEndTimeUnixNano(endNs)
        .setStatus(Status.newBuilder().setCode(status).build())
        .addAttributes(OtelShortHands.keyValue("peer.service", peerService))
        .build();
  }

  public ResourceSpans resourceSpans(String serviceName, List<Span> spans) {
    var resource =
        Resource.newBuilder()
            .addAttributes(OtelShortHands.keyValue("service.name", serviceName))
            .build();
    var scopeSpans = ScopeSpans.newBuilder().addAllSpans(spans).build();
    return ResourceSpans.newBuilder().setResource(resource).addScopeSpans(scopeSpans).build();
  }

  public ExportTraceServiceRequest buildRequest(List<ResourceSpans> resourceSpans) {
    return ExportTraceServiceRequest.newBuilder().addAllResourceSpans(resourceSpans).build();
  }
}

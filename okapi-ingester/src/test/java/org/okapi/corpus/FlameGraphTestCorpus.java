/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.corpus;

import com.google.protobuf.ByteString;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.resource.v1.Resource;
import io.opentelemetry.proto.trace.v1.ResourceSpans;
import io.opentelemetry.proto.trace.v1.ScopeSpans;
import io.opentelemetry.proto.trace.v1.Span;
import io.opentelemetry.proto.trace.v1.Span.SpanKind;
import java.util.List;
import lombok.Getter;

public class FlameGraphTestCorpus {
  @Getter private final byte[] traceId;
  private final long baseNs;

  public FlameGraphTestCorpus(byte[] traceId, long baseNs) {
    if (traceId == null || traceId.length != 16) {
      throw new IllegalArgumentException("traceId must be 16 bytes");
    }
    this.traceId = traceId;
    this.baseNs = baseNs;
  }

  public ExportTraceServiceRequest buildRequest() {
    var resource =
        Resource.newBuilder()
            .addAttributes(
                KeyValue.newBuilder()
                    .setKey("service.name")
                    .setValue(AnyValue.newBuilder().setStringValue("svc-a").build())
                    .build())
            .build();

    var spans =
        List.of(
            span(
                "span-root",
                "",
                SpanKind.SPAN_KIND_SERVER,
                baseNs + 1_000_000_000L,
                baseNs + 3_000_000_000L),
            span(
                "span-child",
                "span-root",
                SpanKind.SPAN_KIND_CLIENT,
                baseNs + 1_100_000_000L,
                baseNs + 2_000_000_000L),
            span(
                "span-grand",
                "span-child",
                SpanKind.SPAN_KIND_INTERNAL,
                baseNs + 1_200_000_000L,
                baseNs + 1_500_000_000L),
            span(
                "span-orphan",
                "missing-parent",
                SpanKind.SPAN_KIND_SERVER,
                baseNs + 500_000_000L,
                baseNs + 800_000_000L));

    var scopeSpans = ScopeSpans.newBuilder().addAllSpans(spans).build();
    var resourceSpans =
        ResourceSpans.newBuilder().setResource(resource).addScopeSpans(scopeSpans).build();
    return ExportTraceServiceRequest.newBuilder().addResourceSpans(resourceSpans).build();
  }

  private Span span(String spanId, String parentSpanId, SpanKind kind, long startNs, long endNs) {
    var builder =
        Span.newBuilder()
            .setTraceId(ByteString.copyFrom(traceId))
            .setSpanId(ByteString.copyFromUtf8(spanId))
            .setKind(kind)
            .setStartTimeUnixNano(startNs)
            .setEndTimeUnixNano(endNs);
    if (parentSpanId != null && !parentSpanId.isEmpty()) {
      builder.setParentSpanId(ByteString.copyFromUtf8(parentSpanId));
    }
    return builder.build();
  }
}

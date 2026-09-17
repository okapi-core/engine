/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.oscar.integ;

import com.google.protobuf.ByteString;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.resource.v1.Resource;
import io.opentelemetry.proto.trace.v1.ResourceSpans;
import io.opentelemetry.proto.trace.v1.ScopeSpans;
import io.opentelemetry.proto.trace.v1.Span;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.UUID;

public final class OtelHelpers {

  private OtelHelpers() {}

  // ── KeyValue builders ──────────────────────────────────────────────────────

  public static KeyValue kv(String key, String value) {
    return KeyValue.newBuilder()
        .setKey(key)
        .setValue(AnyValue.newBuilder().setStringValue(value).build())
        .build();
  }

  public static KeyValue kv(String key, int value) {
    return KeyValue.newBuilder()
        .setKey(key)
        .setValue(AnyValue.newBuilder().setIntValue(value).build())
        .build();
  }

  public static KeyValue kv(String key, double value) {
    return KeyValue.newBuilder()
        .setKey(key)
        .setValue(AnyValue.newBuilder().setDoubleValue(value).build())
        .build();
  }

  public static KeyValue kv(String key, boolean value) {
    return KeyValue.newBuilder()
        .setKey(key)
        .setValue(AnyValue.newBuilder().setBoolValue(value).build())
        .build();
  }

  // ── Resource helpers ───────────────────────────────────────────────────────

  public static Resource serviceResource(String serviceName) {
    return Resource.newBuilder().addAttributes(kv("service.name", serviceName)).build();
  }

  // ── Span / trace builders ──────────────────────────────────────────────────

  public static ResourceSpans resourceSpans(String service, List<Span> spans) {
    return ResourceSpans.newBuilder()
        .setResource(serviceResource(service))
        .addScopeSpans(ScopeSpans.newBuilder().addAllSpans(spans).build())
        .build();
  }

  public static ExportTraceServiceRequest traceRequest(ResourceSpans... rsList) {
    var builder = ExportTraceServiceRequest.newBuilder();
    for (var rs : rsList) builder.addResourceSpans(rs);
    return builder.build();
  }

  // ── ID generators ──────────────────────────────────────────────────────────

  /** Returns a random 16-byte OTel trace ID. */
  public static ByteString traceId() {
    UUID u = UUID.randomUUID();
    ByteBuffer buf = ByteBuffer.allocate(16);
    buf.putLong(u.getMostSignificantBits());
    buf.putLong(u.getLeastSignificantBits());
    return ByteString.copyFrom(buf.array());
  }

  /** Returns a random 8-byte OTel span ID. */
  public static ByteString spanId() {
    ByteBuffer buf = ByteBuffer.allocate(8);
    buf.putLong(UUID.randomUUID().getMostSignificantBits());
    return ByteString.copyFrom(buf.array());
  }
}

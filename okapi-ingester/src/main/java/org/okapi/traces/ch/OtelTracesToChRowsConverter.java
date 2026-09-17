/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.traces.ch;

import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.trace.v1.ScopeSpans;
import io.opentelemetry.proto.trace.v1.Span;
import io.opentelemetry.proto.trace.v1.Status;
import java.util.*;
import org.okapi.nullity.NullHandler;
import org.okapi.otel.OtelAnyValueDecoder;
import org.okapi.otel.OtelAttrDecoder;
import org.okapi.otel.ResourceAttributesReader;

public class OtelTracesToChRowsConverter {
  public List<ChSpansTableRow> toRows(ExportTraceServiceRequest request) {
    if (request == null) return Collections.emptyList();
    List<ChSpansTableRow> rows = new ArrayList<>();
    for (var resourceSpans : request.getResourceSpansList()) {
      var serviceName =
          ResourceAttributesReader.getSvc(resourceSpans.getResource())
              .orElse(ChSpansTableV1Defaults.DEFAULT_SERVICE_NAME);
      for (ScopeSpans scopeSpans : resourceSpans.getScopeSpansList()) {
        for (Span span : scopeSpans.getSpansList()) {
          rows.add(toRow(span, serviceName));
        }
      }
    }
    return rows;
  }

  public List<ChSpansIngestedAttribsRow> toAttributeRows(ExportTraceServiceRequest request) {
    if (request == null) return Collections.emptyList();
    List<ChSpansIngestedAttribsRow> rows = new ArrayList<>();
    for (var resourceSpans : request.getResourceSpansList()) {
      for (var scopeSpans : resourceSpans.getScopeSpansList()) {
        for (var span : scopeSpans.getSpansList()) {
          rows.addAll(toAttributeRows(span));
        }
      }
    }
    return rows;
  }

  public List<ChServiceRedEvents> deriveRedEvents(ExportTraceServiceRequest request) {
    List<ChServiceRedEvents> rows = new ArrayList<>();
    for (var resourceSpans : request.getResourceSpansList()) {
      var svc = ResourceAttributesReader.getSvc(resourceSpans.getResource());
      if (svc.isEmpty()) continue;
      for (var scopeSpans : resourceSpans.getScopeSpansList()) {
        for (var span : scopeSpans.getSpansList()) {
          deriveRedEvent(svc.get(), span).ifPresent(rows::add);
        }
      }
    }
    return rows;
  }

  public Optional<ChServiceRedEvents> deriveRedEvent(String svc, Span span) {
    SpanKind kind =
        switch (NullHandler.ifNullThen(span.getKind(), Span.SpanKind.SPAN_KIND_UNSPECIFIED)) {
          case SPAN_KIND_UNSPECIFIED -> SpanKind.UNK;
          case SPAN_KIND_INTERNAL -> SpanKind.INTERNAL;
          case SPAN_KIND_SERVER -> SpanKind.SERVER;
          case SPAN_KIND_CLIENT -> SpanKind.CLIENT;
          case SPAN_KIND_PRODUCER -> SpanKind.PRODUCER;
          case SPAN_KIND_CONSUMER -> SpanKind.CONSUMER;
          case UNRECOGNIZED -> SpanKind.UNK;
        };
    var status = readStatus(span);
    var startNanos = span.getStartTimeUnixNano();
    var endNanos = span.getEndTimeUnixNano();
    var name = span.getName();
    Map<String, AnyValue> spanAttrs = OtelAttrDecoder.toAttrMap(span.getAttributesList());
    var builder =
        ChServiceRedEvents.builder()
            .tsStartNanos(startNanos)
            .tsEndNanos(endNanos)
            .serviceName(svc)
            .spanName(NullHandler.ifNullThenEmpty(name))
            .spanKind(kind);
    OtelAttrDecoder.getStringStrict(spanAttrs, ChSpansFallbackPaths.getPaths("service_peer_name"))
        .ifPresent(builder::peerServiceName);
    return status.map(spanStatus -> builder.spanStatus(spanStatus).build());
  }

  private ChSpansTableRow toRow(Span span, String serviceName) {
    Map<String, AnyValue> spanAttrs = OtelAttrDecoder.toAttrMap(span.getAttributesList());
    var kind = span.getKind().name();
    var strBuckets = initStringBuckets();
    var numberBuckets = initNumberBuckets();
    populateAttributeBuckets(spanAttrs, strBuckets, numberBuckets);
    var spanStatus = readStatus(span).orElse(SpanStatus.UNK);
    var builder =
        ChSpansTableRow.builder()
            .ts_start_ns(span.getStartTimeUnixNano())
            .ts_end_ns(span.getEndTimeUnixNano())
            .span_id(OtelAnyValueDecoder.bytesToHex(span.getSpanId().toByteArray()))
            .span_status(spanStatus)
            .parent_span_id(OtelAnyValueDecoder.bytesToHex(span.getParentSpanId().toByteArray()))
            .trace_id(OtelAnyValueDecoder.bytesToHex(span.getTraceId().toByteArray()))
            .kind(kind)
            .kind_string(kind)
            .service_name(
                serviceName == null || serviceName.isEmpty()
                    ? ChSpansTableV1Defaults.DEFAULT_SERVICE_NAME
                    : serviceName);

    OtelAttrDecoder.getStringStrict(spanAttrs, ChSpansFallbackPaths.getPaths("service_peer_name"))
        .ifPresent(builder::service_peer_name);
    OtelAttrDecoder.getStringStrict(spanAttrs, ChSpansFallbackPaths.getPaths("http_method"))
        .ifPresent(builder::http_method);
    OtelAttrDecoder.getIntStrict(spanAttrs, ChSpansFallbackPaths.getPaths("http_status_code"))
        .ifPresent(builder::http_status_code);
    OtelAttrDecoder.getIntStrict(spanAttrs, ChSpansFallbackPaths.getPaths("http_request_size"))
        .ifPresent(builder::http_request_size);
    OtelAttrDecoder.getIntStrict(spanAttrs, ChSpansFallbackPaths.getPaths("http_response_size"))
        .ifPresent(builder::http_response_size);
    OtelAttrDecoder.getStringStrict(spanAttrs, ChSpansFallbackPaths.getPaths("http_origin"))
        .ifPresent(builder::http_origin);
    OtelAttrDecoder.getStringStrict(spanAttrs, ChSpansFallbackPaths.getPaths("http_host"))
        .ifPresent(builder::http_host);
    OtelAttrDecoder.getIntStrict(spanAttrs, ChSpansFallbackPaths.getPaths("server_address"))
        .ifPresent(builder::server_address);
    OtelAttrDecoder.getIntStrict(spanAttrs, ChSpansFallbackPaths.getPaths("server_port"))
        .ifPresent(builder::server_port);
    OtelAttrDecoder.getStringStrict(spanAttrs, ChSpansFallbackPaths.getPaths("client_address"))
        .ifPresent(builder::client_address);
    OtelAttrDecoder.getIntStrict(spanAttrs, ChSpansFallbackPaths.getPaths("client_port"))
        .ifPresent(builder::client_port);
    OtelAttrDecoder.getStringStrict(spanAttrs, ChSpansFallbackPaths.getPaths("source_address"))
        .ifPresent(builder::source_address);
    OtelAttrDecoder.getIntStrict(spanAttrs, ChSpansFallbackPaths.getPaths("source_port"))
        .ifPresent(builder::source_port);
    OtelAttrDecoder.getStringStrict(
            spanAttrs, ChSpansFallbackPaths.getPaths("network_protocol_type"))
        .ifPresent(builder::network_protocol_type);
    OtelAttrDecoder.getStringStrict(
            spanAttrs, ChSpansFallbackPaths.getPaths("network_protocol_version"))
        .ifPresent(builder::network_protocol_version);
    OtelAttrDecoder.getStringStrict(spanAttrs, ChSpansFallbackPaths.getPaths("db_system_name"))
        .ifPresent(builder::db_system_name);
    OtelAttrDecoder.getStringStrict(spanAttrs, ChSpansFallbackPaths.getPaths("db_collection_name"))
        .ifPresent(builder::db_collection_name);
    OtelAttrDecoder.getStringStrict(spanAttrs, ChSpansFallbackPaths.getPaths("db_namespace"))
        .ifPresent(builder::db_namespace);
    OtelAttrDecoder.getStringStrict(spanAttrs, ChSpansFallbackPaths.getPaths("db_operation_name"))
        .ifPresent(builder::db_operation_name);
    OtelAttrDecoder.getIntStrict(
            spanAttrs, ChSpansFallbackPaths.getPaths("db_response_status_code"))
        .ifPresent(builder::db_response_status_code);
    OtelAttrDecoder.getStringStrict(spanAttrs, ChSpansFallbackPaths.getPaths("db_query_text"))
        .ifPresent(builder::db_query_text);
    OtelAttrDecoder.getStringStrict(spanAttrs, ChSpansFallbackPaths.getPaths("db_query_summary"))
        .ifPresent(builder::db_query_summary);
    OtelAttrDecoder.getStringStrict(
            spanAttrs, ChSpansFallbackPaths.getPaths("db_stored_procedure_name"))
        .ifPresent(builder::db_stored_procedure_name);
    OtelAttrDecoder.getIntStrict(
            spanAttrs, ChSpansFallbackPaths.getPaths("db_response_returned_rows"))
        .ifPresent(builder::db_response_returned_rows);
    OtelAttrDecoder.getStringStrict(spanAttrs, ChSpansFallbackPaths.getPaths("rpc_method"))
        .ifPresent(builder::rpc_method);
    OtelAttrDecoder.getStringStrict(spanAttrs, ChSpansFallbackPaths.getPaths("rpc_method_original"))
        .ifPresent(builder::rpc_method_original);
    OtelAttrDecoder.getIntStrict(
            spanAttrs, ChSpansFallbackPaths.getPaths("rpc_response_status_code"))
        .ifPresent(builder::rpc_response_status_code);

    builder
        .attribs_str_0(strBuckets.get(0))
        .attribs_str_1(strBuckets.get(1))
        .attribs_str_2(strBuckets.get(2))
        .attribs_str_3(strBuckets.get(3))
        .attribs_str_4(strBuckets.get(4))
        .attribs_str_5(strBuckets.get(5))
        .attribs_str_6(strBuckets.get(6))
        .attribs_str_7(strBuckets.get(7))
        .attribs_str_8(strBuckets.get(8))
        .attribs_str_9(strBuckets.get(9))
        .attribs_number_0(numberBuckets.get(0))
        .attribs_number_1(numberBuckets.get(1))
        .attribs_number_2(numberBuckets.get(2))
        .attribs_number_3(numberBuckets.get(3))
        .attribs_number_4(numberBuckets.get(4))
        .attribs_number_5(numberBuckets.get(5))
        .attribs_number_6(numberBuckets.get(6))
        .attribs_number_7(numberBuckets.get(7))
        .attribs_number_8(numberBuckets.get(8))
        .attribs_number_9(numberBuckets.get(9));

    return builder.build();
  }

  private static Optional<SpanStatus> readStatus(Span span) {
    return Optional.ofNullable(span)
        .map(Span::getStatus)
        .map(Status::getCode)
        .map(
            statusCode ->
                switch (statusCode) {
                  case STATUS_CODE_UNSET -> SpanStatus.UNK;
                  case STATUS_CODE_OK -> SpanStatus.OK;
                  case STATUS_CODE_ERROR -> SpanStatus.ERROR;
                  case UNRECOGNIZED -> SpanStatus.UNK;
                });
  }

  private static List<ChSpansIngestedAttribsRow> toAttributeRows(Span span) {
    if (span == null) return Collections.emptyList();
    Map<String, AnyValue> spanAttrs = OtelAttrDecoder.toAttrMap(span.getAttributesList());
    if (spanAttrs.isEmpty()) return Collections.emptyList();
    long tsStart = span.getStartTimeUnixNano();
    long tsEnd = span.getEndTimeUnixNano();
    var stringSeen = new HashSet<String>();
    var numberSeen = new HashSet<String>();
    var rows = new ArrayList<ChSpansIngestedAttribsRow>();
    for (var entry : spanAttrs.entrySet()) {
      var key = entry.getKey();
      if (ChSpanAttributeBucketer.isReservedKey(key)) continue;
      var value = entry.getValue();
      if (value == null) continue;
      switch (value.getValueCase()) {
        case STRING_VALUE -> {
          if (!stringSeen.add(key)) continue;
          rows.add(
              ChSpansIngestedAttribsRow.builder()
                  .ts_start_ns(tsStart)
                  .ts_end_ns(tsEnd)
                  .attribute_name(key)
                  .attribute_type("string")
                  .build());
        }
        case INT_VALUE, DOUBLE_VALUE -> {
          if (!numberSeen.add(key)) continue;
          rows.add(
              ChSpansIngestedAttribsRow.builder()
                  .ts_start_ns(tsStart)
                  .ts_end_ns(tsEnd)
                  .attribute_name(key)
                  .attribute_type("number")
                  .build());
        }
        default -> {}
      }
    }
    return rows;
  }

  private static List<Map<String, String>> initStringBuckets() {
    var buckets = new ArrayList<Map<String, String>>(ChSpanAttributeBucketer.BUCKETS);
    for (int i = 0; i < ChSpanAttributeBucketer.BUCKETS; i++) {
      buckets.add(new HashMap<>());
    }
    return buckets;
  }

  private static List<Map<String, Double>> initNumberBuckets() {
    var buckets = new ArrayList<Map<String, Double>>(ChSpanAttributeBucketer.BUCKETS);
    for (int i = 0; i < ChSpanAttributeBucketer.BUCKETS; i++) {
      buckets.add(new HashMap<>());
    }
    return buckets;
  }

  private static void populateAttributeBuckets(
      Map<String, AnyValue> spanAttrs,
      List<Map<String, String>> strBuckets,
      List<Map<String, Double>> numberBuckets) {
    if (spanAttrs == null || spanAttrs.isEmpty()) return;
    for (var entry : spanAttrs.entrySet()) {
      var key = entry.getKey();
      if (ChSpanAttributeBucketer.isReservedKey(key)) continue;
      var value = entry.getValue();
      if (value == null) continue;
      int bucket = ChSpanAttributeBucketer.bucketForKey(key);
      switch (value.getValueCase()) {
        case STRING_VALUE -> strBuckets.get(bucket).put(key, value.getStringValue());
        case INT_VALUE -> numberBuckets.get(bucket).put(key, (double) value.getIntValue());
        case DOUBLE_VALUE -> numberBuckets.get(bucket).put(key, value.getDoubleValue());
        default -> {}
      }
    }
  }
}

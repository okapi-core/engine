/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.logs.ch;

import io.opentelemetry.proto.collector.logs.v1.ExportLogsServiceRequest;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.logs.v1.LogRecord;
import io.opentelemetry.proto.logs.v1.ResourceLogs;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.okapi.otel.OtelAnyValueDecoder;
import org.okapi.otel.OtelAttrDecoder;
import org.okapi.otel.ResourceAttributesReader;

public class OtelLogsToChRowsConverter {
  private static final String LOG_STREAM_ATTR = "log.stream";
  public static final String DEFAULT_STREAM = "okapi.no_set_stream";

  public List<ChLogsTableRow> toRows(ExportLogsServiceRequest request) {
    if (request == null || request.getResourceLogsList().isEmpty()) {
      return List.of();
    }
    var rows = new ArrayList<ChLogsTableRow>();
    for (var resourceLogs : request.getResourceLogsList()) {
      rows.addAll(toRows(resourceLogs));
    }
    return rows;
  }

  private List<ChLogsTableRow> toRows(ResourceLogs resourceLogs) {
    var maybeService = ResourceAttributesReader.getSvc(resourceLogs.getResource());
    if (maybeService.isEmpty()) {
      return List.of();
    }
    var serviceName = maybeService.get();
    var resourceAttrs = OtelAttrDecoder.toAttrMap(resourceLogs.getResource().getAttributesList());
    var resourceStream = getString(resourceAttrs, LOG_STREAM_ATTR);

    var rows = new ArrayList<ChLogsTableRow>();
    for (var scopeLogs : resourceLogs.getScopeLogsList()) {
      for (var record : scopeLogs.getLogRecordsList()) {
        rows.add(toRow(serviceName, resourceStream, resourceAttrs, record));
      }
    }
    return rows;
  }

  private ChLogsTableRow toRow(
      String serviceName,
      String resourceStream,
      Map<String, AnyValue> resourceAttrs,
      LogRecord record) {
    var recordAttrs = OtelAttrDecoder.toAttrMap(record.getAttributesList());
    var resourceStrBuckets = initStringBuckets();
    var resourceNumberBuckets = initNumberBuckets();
    var recordStrBuckets = initStringBuckets();
    var recordNumberBuckets = initNumberBuckets();
    populateAttributeBuckets(resourceAttrs, resourceStrBuckets, resourceNumberBuckets);
    populateAttributeBuckets(recordAttrs, recordStrBuckets, recordNumberBuckets);
    var logStream =
        firstNonBlank(getString(recordAttrs, LOG_STREAM_ATTR), resourceStream, serviceName);
    var builder =
        ChLogsTableRow.builder()
            .ts_ns(record.getTimeUnixNano())
            .log_stream(logStream)
            .service_name(serviceName)
            .log_level(record.getSeverityNumber().getNumber())
            .severity_text(record.getSeverityText())
            .trace_id(OtelAnyValueDecoder.bytesToHex(record.getTraceId().toByteArray()))
            .span_id(OtelAnyValueDecoder.bytesToHex(record.getSpanId().toByteArray()))
            .body(OtelAnyValueDecoder.decodeAsString(record.getBody()).orElse(""));
    populateResourceBuckets(builder, resourceStrBuckets, resourceNumberBuckets);
    populateRecordBuckets(builder, recordStrBuckets, recordNumberBuckets);
    return builder.build();
  }

  private static String getString(Map<String, AnyValue> attrs, String key) {
    return OtelAttrDecoder.getStringStrict(attrs, List.of(key)).orElse(null);
  }

  private static String firstNonBlank(String... values) {
    for (var value : values) {
      if (value != null && !value.isBlank()) {
        return value;
      }
    }
    return DEFAULT_STREAM;
  }

  private static List<Map<String, String>> initStringBuckets() {
    var buckets = new ArrayList<Map<String, String>>(ChLogsAttributeBucketer.BUCKETS);
    for (int i = 0; i < ChLogsAttributeBucketer.BUCKETS; i++) {
      buckets.add(new HashMap<>());
    }
    return buckets;
  }

  private static List<Map<String, Double>> initNumberBuckets() {
    var buckets = new ArrayList<Map<String, Double>>(ChLogsAttributeBucketer.BUCKETS);
    for (int i = 0; i < ChLogsAttributeBucketer.BUCKETS; i++) {
      buckets.add(new HashMap<>());
    }
    return buckets;
  }

  private static void populateAttributeBuckets(
      Map<String, AnyValue> attrs,
      List<Map<String, String>> strBuckets,
      List<Map<String, Double>> numberBuckets) {
    if (attrs == null || attrs.isEmpty()) {
      return;
    }
    for (var entry : attrs.entrySet()) {
      var key = entry.getKey();
      if (ChLogsAttributeBucketer.isReservedKey(key)) {
        continue;
      }
      var value = entry.getValue();
      if (value == null) {
        continue;
      }
      int bucket = ChLogsAttributeBucketer.bucketForKey(key);
      switch (value.getValueCase()) {
        case STRING_VALUE -> strBuckets.get(bucket).put(key, value.getStringValue());
        case INT_VALUE -> numberBuckets.get(bucket).put(key, (double) value.getIntValue());
        case DOUBLE_VALUE -> numberBuckets.get(bucket).put(key, value.getDoubleValue());
        default -> {}
      }
    }
  }

  private static void populateResourceBuckets(
      ChLogsTableRow.ChLogsTableRowBuilder builder,
      List<Map<String, String>> strBuckets,
      List<Map<String, Double>> numberBuckets) {
    builder
        .resource_attribs_str_0(strBuckets.get(0))
        .resource_attribs_str_1(strBuckets.get(1))
        .resource_attribs_str_2(strBuckets.get(2))
        .resource_attribs_str_3(strBuckets.get(3))
        .resource_attribs_str_4(strBuckets.get(4))
        .resource_attribs_str_5(strBuckets.get(5))
        .resource_attribs_str_6(strBuckets.get(6))
        .resource_attribs_str_7(strBuckets.get(7))
        .resource_attribs_str_8(strBuckets.get(8))
        .resource_attribs_str_9(strBuckets.get(9))
        .resource_attribs_number_0(numberBuckets.get(0))
        .resource_attribs_number_1(numberBuckets.get(1))
        .resource_attribs_number_2(numberBuckets.get(2))
        .resource_attribs_number_3(numberBuckets.get(3))
        .resource_attribs_number_4(numberBuckets.get(4))
        .resource_attribs_number_5(numberBuckets.get(5))
        .resource_attribs_number_6(numberBuckets.get(6))
        .resource_attribs_number_7(numberBuckets.get(7))
        .resource_attribs_number_8(numberBuckets.get(8))
        .resource_attribs_number_9(numberBuckets.get(9));
  }

  private static void populateRecordBuckets(
      ChLogsTableRow.ChLogsTableRowBuilder builder,
      List<Map<String, String>> strBuckets,
      List<Map<String, Double>> numberBuckets) {
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
  }
}

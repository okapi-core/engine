/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.logs.ch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.okapi.otelshorthand.OtelShortHands.keyValue;

import com.google.protobuf.ByteString;
import io.opentelemetry.proto.collector.logs.v1.ExportLogsServiceRequest;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.logs.v1.LogRecord;
import io.opentelemetry.proto.logs.v1.ResourceLogs;
import io.opentelemetry.proto.logs.v1.ScopeLogs;
import io.opentelemetry.proto.logs.v1.SeverityNumber;
import io.opentelemetry.proto.resource.v1.Resource;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.okapi.otelshorthand.OtelShortHands;

class OtelLogsToChRowsConverterTests {
  private final OtelLogsToChRowsConverter converter = new OtelLogsToChRowsConverter();

  @Test
  void convertsResourceLogRecordsToClickHouseRows() {
    var request =
        ExportLogsServiceRequest.newBuilder()
            .addResourceLogs(
                resourceLogs(
                    "checkout-api",
                    "resource-stream",
                    List.of(
                        log(
                            123L,
                            SeverityNumber.SEVERITY_NUMBER_ERROR_VALUE,
                            "payment failed",
                            "record-stream",
                            List.of(
                                keyValue("http.route", "/checkout"),
                                keyValue("http.status_code", 503),
                                keyValue("duration_ms", 12.5))))))
            .build();

    var rows = converter.toRows(request);

    assertEquals(1, rows.size());
    var row = rows.getFirst();
    assertEquals(123L, row.getTs_ns());
    assertEquals("record-stream", row.getLog_stream());
    assertEquals("checkout-api", row.getService_name());
    assertEquals(SeverityNumber.SEVERITY_NUMBER_ERROR_VALUE, row.getLog_level());
    assertEquals("ERROR", row.getSeverity_text());
    assertEquals("010203", row.getTrace_id());
    assertEquals("040506", row.getSpan_id());
    assertEquals("payment failed", row.getBody());
    assertEquals(
        "prod", resourceStringBucket(row, "deployment.environment").get("deployment.environment"));
    assertEquals("/checkout", recordStringBucket(row, "http.route").get("http.route"));
    assertEquals(503.0, recordNumberBucket(row, "http.status_code").get("http.status_code"));
    assertEquals(12.5, recordNumberBucket(row, "duration_ms").get("duration_ms"));
    assertFalse(recordStringBucket(row, "log.stream").containsKey("log.stream"));
  }

  @Test
  void usesResourceStreamThenServiceNameAsStreamFallback() {
    var request =
        ExportLogsServiceRequest.newBuilder()
            .addResourceLogs(
                resourceLogs(
                    "checkout-api",
                    "resource-stream",
                    List.of(log(1L, SeverityNumber.SEVERITY_NUMBER_INFO_VALUE, "one", null))))
            .addResourceLogs(
                resourceLogs(
                    "worker",
                    null,
                    List.of(log(2L, SeverityNumber.SEVERITY_NUMBER_INFO_VALUE, "two", null))))
            .build();

    var rows = converter.toRows(request);

    assertEquals("resource-stream", rows.get(0).getLog_stream());
    assertEquals("worker", rows.get(1).getLog_stream());
  }

  @Test
  void skipsResourceLogsWithoutServiceName() {
    var request =
        ExportLogsServiceRequest.newBuilder()
            .addResourceLogs(
                ResourceLogs.newBuilder()
                    .addScopeLogs(
                        ScopeLogs.newBuilder()
                            .addLogRecords(
                                log(1L, SeverityNumber.SEVERITY_NUMBER_INFO_VALUE, "body", null))))
            .build();

    assertEquals(0, converter.toRows(request).size());
  }

  private ResourceLogs resourceLogs(String service, String stream, List<LogRecord> records) {
    var resource =
        Resource.newBuilder()
            .addAttributes(keyValue("service.name", service))
            .addAttributes(keyValue("deployment.environment", "prod"));
    if (stream != null) {
      resource.addAttributes(keyValue("log.stream", stream));
    }
    return ResourceLogs.newBuilder()
        .setResource(resource)
        .addScopeLogs(ScopeLogs.newBuilder().addAllLogRecords(records))
        .build();
  }

  private LogRecord log(long tsNs, int severity, String body, String stream) {
    return log(tsNs, severity, body, stream, List.of());
  }

  private LogRecord log(
      long tsNs, int severity, String body, String stream, List<KeyValue> extraAttrs) {
    var builder =
        LogRecord.newBuilder()
            .setTimeUnixNano(tsNs)
            .setSeverityNumber(SeverityNumber.forNumber(severity))
            .setSeverityText("ERROR")
            .setTraceId(ByteString.copyFrom(new byte[] {1, 2, 3}))
            .setSpanId(ByteString.copyFrom(new byte[] {4, 5, 6}))
            .setBody(OtelShortHands.strValue(body));
    if (stream != null) {
      builder.addAttributes(keyValue("log.stream", stream));
    }
    builder.addAllAttributes(extraAttrs);
    return builder.build();
  }

  private Map<String, String> resourceStringBucket(ChLogsTableRow row, String key) {
    return switch (ChLogsAttributeBucketer.bucketForKey(key)) {
      case 0 -> row.getResource_attribs_str_0();
      case 1 -> row.getResource_attribs_str_1();
      case 2 -> row.getResource_attribs_str_2();
      case 3 -> row.getResource_attribs_str_3();
      case 4 -> row.getResource_attribs_str_4();
      case 5 -> row.getResource_attribs_str_5();
      case 6 -> row.getResource_attribs_str_6();
      case 7 -> row.getResource_attribs_str_7();
      case 8 -> row.getResource_attribs_str_8();
      case 9 -> row.getResource_attribs_str_9();
      default -> throw new IllegalStateException("invalid bucket");
    };
  }

  private Map<String, String> recordStringBucket(ChLogsTableRow row, String key) {
    return switch (ChLogsAttributeBucketer.bucketForKey(key)) {
      case 0 -> row.getAttribs_str_0();
      case 1 -> row.getAttribs_str_1();
      case 2 -> row.getAttribs_str_2();
      case 3 -> row.getAttribs_str_3();
      case 4 -> row.getAttribs_str_4();
      case 5 -> row.getAttribs_str_5();
      case 6 -> row.getAttribs_str_6();
      case 7 -> row.getAttribs_str_7();
      case 8 -> row.getAttribs_str_8();
      case 9 -> row.getAttribs_str_9();
      default -> throw new IllegalStateException("invalid bucket");
    };
  }

  private Map<String, Double> recordNumberBucket(ChLogsTableRow row, String key) {
    return switch (ChLogsAttributeBucketer.bucketForKey(key)) {
      case 0 -> row.getAttribs_number_0();
      case 1 -> row.getAttribs_number_1();
      case 2 -> row.getAttribs_number_2();
      case 3 -> row.getAttribs_number_3();
      case 4 -> row.getAttribs_number_4();
      case 5 -> row.getAttribs_number_5();
      case 6 -> row.getAttribs_number_6();
      case 7 -> row.getAttribs_number_7();
      case 8 -> row.getAttribs_number_8();
      case 9 -> row.getAttribs_number_9();
      default -> throw new IllegalStateException("invalid bucket");
    };
  }
}

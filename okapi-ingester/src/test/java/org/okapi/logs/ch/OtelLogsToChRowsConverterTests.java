/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.logs.ch;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.opentelemetry.proto.collector.logs.v1.ExportLogsServiceRequest;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.logs.v1.LogRecord;
import io.opentelemetry.proto.logs.v1.ResourceLogs;
import io.opentelemetry.proto.logs.v1.ScopeLogs;
import io.opentelemetry.proto.logs.v1.SeverityNumber;
import io.opentelemetry.proto.resource.v1.Resource;
import java.util.List;
import org.junit.jupiter.api.Test;

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
                            "record-stream"))))
            .build();

    var rows = converter.toRows(request);

    assertEquals(1, rows.size());
    var row = rows.getFirst();
    assertEquals(123L, row.getTs_ns());
    assertEquals("record-stream", row.getLog_stream());
    assertEquals("checkout-api", row.getService_name());
    assertEquals(SeverityNumber.SEVERITY_NUMBER_ERROR_VALUE, row.getLog_level());
    assertEquals("payment failed", row.getBody());
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
    var resource = Resource.newBuilder().addAttributes(attr("service.name", service));
    if (stream != null) {
      resource.addAttributes(attr("log.stream", stream));
    }
    return ResourceLogs.newBuilder()
        .setResource(resource)
        .addScopeLogs(ScopeLogs.newBuilder().addAllLogRecords(records))
        .build();
  }

  private LogRecord log(long tsNs, int severity, String body, String stream) {
    var builder =
        LogRecord.newBuilder()
            .setTimeUnixNano(tsNs)
            .setSeverityNumber(SeverityNumber.forNumber(severity))
            .setBody(AnyValue.newBuilder().setStringValue(body));
    if (stream != null) {
      builder.addAttributes(attr("log.stream", stream));
    }
    return builder.build();
  }

  private KeyValue attr(String key, String value) {
    return KeyValue.newBuilder()
        .setKey(key)
        .setValue(AnyValue.newBuilder().setStringValue(value))
        .build();
  }
}

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
import java.util.List;
import java.util.Map;
import org.okapi.otel.OtelAnyValueDecoder;
import org.okapi.otel.OtelAttrDecoder;
import org.okapi.otel.ResourceAttributesReader;

public class OtelLogsToChRowsConverter {
  private static final String LOG_STREAM_ATTR = "log.stream";

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
        rows.add(toRow(serviceName, resourceStream, record));
      }
    }
    return rows;
  }

  private ChLogsTableRow toRow(String serviceName, String resourceStream, LogRecord record) {
    var recordAttrs = OtelAttrDecoder.toAttrMap(record.getAttributesList());
    var logStream = firstNonBlank(getString(recordAttrs, LOG_STREAM_ATTR), resourceStream, serviceName);
    return ChLogsTableRow.builder()
        .ts_ns(record.getTimeUnixNano())
        .log_stream(logStream)
        .service_name(serviceName)
        .log_level(record.getSeverityNumber().getNumber())
        .body(OtelAnyValueDecoder.decodeAsString(record.getBody()).orElse(""))
        .build();
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
    return "";
  }
}

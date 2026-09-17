/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.logs.ch;

import static org.okapi.otelshorthand.OtelShortHands.keyValue;

import io.opentelemetry.proto.collector.logs.v1.ExportLogsServiceRequest;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.logs.v1.LogRecord;
import io.opentelemetry.proto.logs.v1.ResourceLogs;
import io.opentelemetry.proto.logs.v1.ScopeLogs;
import io.opentelemetry.proto.logs.v1.SeverityNumber;
import io.opentelemetry.proto.resource.v1.Resource;
import java.util.List;
import java.util.Map;
import org.okapi.rest.common.UnionValue;
import org.okapi.rest.logs.ChLogFilter;
import org.okapi.rest.logs.ChLogFilterOp;
import org.okapi.rest.logs.ChLogsQueryRequest;

public class ChLogsCorpus {
  public static final long BASE_NS = 1_000_000_000L;
  public static final long HOUR_BOUNDARY_NS = 3_600_000_000_000L;

  public static ExportLogsServiceRequest buildCorpus() {

    return ExportLogsServiceRequest.newBuilder()
        .addResourceLogs(
            resourceLogs(
                "checkout-api",
                "prod-us-east",
                List.of(
                    log(
                        "line-1",
                        BASE_NS + 1,
                        SeverityNumber.SEVERITY_NUMBER_INFO_VALUE,
                        "Order confirmed order=1001 user=u123",
                        List.of(
                            keyValue("http.route", "/checkout"),
                            keyValue("http.status_code", 200),
                            keyValue("duration_ms", 12.5))),
                    log(
                        "line-2",
                        BASE_NS + 2,
                        SeverityNumber.SEVERITY_NUMBER_DEBUG_VALUE,
                        "PricingEngine applied discount code=SPRING10 order=1001",
                        Map.of()),
                    log(
                        "line-4",
                        BASE_NS + 4,
                        SeverityNumber.SEVERITY_NUMBER_ERROR_VALUE,
                        "Payment authorization failed order=2002 provider=stripe",
                        Map.of()),
                    log(
                        "line-5",
                        BASE_NS + 7,
                        SeverityNumber.SEVERITY_NUMBER_ERROR_VALUE,
                        "Order creation failed order=2002 cause=payment_error",
                        Map.of()))))
        .addResourceLogs(
            resourceLogs(
                "catalog-api",
                "prod-eu-west",
                List.of(
                    log(
                        "line-3",
                        BASE_NS + 3,
                        SeverityNumber.SEVERITY_NUMBER_WARN_VALUE,
                        "Inventory low for sku=SHOE-RED-42 remaining=2",
                        Map.of()))))
        .addResourceLogs(
            resourceLogs(
                "worker",
                null,
                List.of(
                    log(
                        "line-6",
                        BASE_NS + 5,
                        SeverityNumber.SEVERITY_NUMBER_INFO_VALUE,
                        "Worker path O'Reilly\\ops",
                        Map.of("log.stream", "batch-payments")),
                    log(
                        "line-7",
                        HOUR_BOUNDARY_NS + 5_000_000L,
                        SeverityNumber.SEVERITY_NUMBER_INFO_VALUE,
                        "Boundary log after first hour",
                        Map.of("log.stream", "boundary")))))
        .build();
  }

  public static ResourceLogs resourceLogs(String service, String stream, List<LogRecord> records) {
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

  public static LogRecord log(
      String lineId, long tsNs, int severity, String body, Map<String, String> attrs) {
    return log(
        lineId,
        tsNs,
        severity,
        body,
        attrs.entrySet().stream()
            .map(entry -> keyValue(entry.getKey(), entry.getValue()))
            .toList());
  }

  public static LogRecord log(
      String lineId, long tsNs, int severity, String body, List<KeyValue> attrs) {
    var builder =
        LogRecord.newBuilder()
            .setTimeUnixNano(tsNs)
            .setSeverityNumber(SeverityNumber.forNumber(severity))
            .setBody(AnyValue.newBuilder().setStringValue(body))
            .addAttributes(keyValue("line_id", lineId));
    builder.addAllAttributes(attrs);
    return builder.build();
  }

  public static ChLogsQueryRequest request(List<ChLogFilter> filters, int limit) {
    return ChLogsQueryRequest.builder()
        .tsStartNanos(0L)
        .tsEndNanos(HOUR_BOUNDARY_NS + 1_000_000_000L)
        .filters(filters)
        .limit(limit)
        .build();
  }

  public static ChLogFilter exact(String key, String value) {
    return ChLogFilter.builder()
        .key(key)
        .op(ChLogFilterOp.EQ)
        .value(UnionValue.stringValue(value))
        .build();
  }

  public static ChLogFilter regex(String key, String value) {
    return ChLogFilter.builder()
        .key(key)
        .op(ChLogFilterOp.REGEX)
        .value(UnionValue.stringValue(value))
        .build();
  }

  public static ChLogFilter string(String key, ChLogFilterOp op, String value) {
    return ChLogFilter.builder().key(key).op(op).value(UnionValue.stringValue(value)).build();
  }

  public static ChLogFilter number(String key, ChLogFilterOp op, int value) {
    return ChLogFilter.builder().key(key).op(op).value(UnionValue.integerValue(value)).build();
  }
}

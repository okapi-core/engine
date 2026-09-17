/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.logs;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.okapi.rest.common.UnionValue;

@AllArgsConstructor
@Builder
@Getter
@NoArgsConstructor
@ToString
@JsonClassDescription("A log record returned by a log search.")
public class ChLogRow {
  @JsonPropertyDescription("Log timestamp in nanoseconds since Unix epoch.")
  long tsNanos;

  @JsonPropertyDescription("Logical log stream containing this record.")
  String logStream;

  @JsonPropertyDescription("Name of the service that emitted the log record.")
  String serviceName;

  @JsonPropertyDescription(
      "OpenTelemetry severity number, for example DEBUG=5, INFO=9, WARN=13, or ERROR=17.")
  int logLevel;

  @JsonPropertyDescription("OpenTelemetry severity text, for example INFO, WARN, or ERROR.")
  String severityText;

  @JsonPropertyDescription("Trace id correlated with this log record, if present.")
  String traceId;

  @JsonPropertyDescription("Span id correlated with this log record, if present.")
  String spanId;

  @JsonPropertyDescription("Rendered text body of the log record.")
  String body;

  @JsonPropertyDescription("Structured resource attributes on this log record.")
  Map<String, UnionValue> resourceAttributes;

  @JsonPropertyDescription("Structured log record attributes.")
  Map<String, UnionValue> attributes;
}

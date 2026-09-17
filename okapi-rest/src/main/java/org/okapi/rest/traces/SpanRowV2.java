/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.traces;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.Map;
import lombok.*;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@JsonClassDescription("A single span, representing one unit of work within a distributed trace.")
@ToString
public class SpanRowV2 {
  @JsonPropertyDescription("Span start timestamp in nanoseconds since Unix epoch.")
  long tsStartNs;

  @JsonPropertyDescription("Span end timestamp in nanoseconds since Unix epoch.")
  long tsEndNs;

  @JsonPropertyDescription("Hex-encoded trace ID this span belongs to.")
  String traceId;

  @JsonPropertyDescription("Hex-encoded unique identifier for this span.")
  String spanId;

  @JsonPropertyDescription("Span outcome: OK, ERROR, or UNK (unknown).")
  SpanStatus spanStatus;

  @JsonPropertyDescription("Hex-encoded span ID of the parent span, or null for root spans.")
  String parentSpanId;

  @JsonPropertyDescription("Numeric span kind code.")
  String kind;

  @JsonPropertyDescription(
      "Human-readable span kind: CLIENT, SERVER, PRODUCER, CONSUMER, or INTERNAL.")
  String kindString;

  @JsonPropertyDescription("Name of the service that emitted this span.")
  String serviceName;

  @JsonPropertyDescription("Name of the downstream service this span called.")
  String servicePeerName;

  @JsonPropertyDescription("HTTP request method (e.g. GET, POST).")
  String httpMethod;

  @JsonPropertyDescription("HTTP response status code.")
  Integer httpStatusCode;

  @JsonPropertyDescription("HTTP request body size in bytes.")
  Integer httpRequestSize;

  @JsonPropertyDescription("HTTP response body size in bytes.")
  Integer httpResponseSize;

  @JsonPropertyDescription("HTTP request origin header value.")
  String httpOrigin;

  @JsonPropertyDescription("HTTP target host.")
  String httpHost;

  @JsonPropertyDescription("Server address.")
  Integer serverAddress;

  @JsonPropertyDescription("Server port number.")
  Integer serverPort;

  @JsonPropertyDescription("Client address.")
  String clientAddress;

  @JsonPropertyDescription("Client port number.")
  Integer clientPort;

  @JsonPropertyDescription("Source network address.")
  String sourceAddress;

  @JsonPropertyDescription("Source network port.")
  Integer sourcePort;

  @JsonPropertyDescription("Network protocol type (e.g. tcp, udp).")
  String networkProtocolType;

  @JsonPropertyDescription("Network protocol version.")
  String networkProtocolVersion;

  @JsonPropertyDescription("Database system (e.g. postgresql, redis, mongodb).")
  String dbSystemName;

  @JsonPropertyDescription("Database collection or table name.")
  String dbCollectionName;

  @JsonPropertyDescription("Database namespace or schema.")
  String dbNamespace;

  @JsonPropertyDescription("Database operation performed (e.g. SELECT, find).")
  String dbOperationName;

  @JsonPropertyDescription("Database response status code.")
  Integer dbResponseStatusCode;

  @JsonPropertyDescription("Full text of the database query.")
  String dbQueryText;

  @JsonPropertyDescription("Summarised form of the database query.")
  String dbQuerySummary;

  @JsonPropertyDescription("Stored procedure name, if applicable.")
  String dbStoredProcedureName;

  @JsonPropertyDescription("Number of rows returned by the database query.")
  Integer dbResponseReturnedRows;

  @JsonPropertyDescription("RPC method name.")
  String rpcMethod;

  @JsonPropertyDescription("Original RPC method name before any transformation.")
  String rpcMethodOriginal;

  @JsonPropertyDescription("RPC response status code.")
  Integer rpcResponseStatusCode;

  @JsonPropertyDescription("Custom span attributes with string values, keyed by attribute name.")
  Map<String, String> customStringAttributes;

  @JsonPropertyDescription("Custom span attributes with numeric values, keyed by attribute name.")
  Map<String, Double> customNumberAttributes;
}

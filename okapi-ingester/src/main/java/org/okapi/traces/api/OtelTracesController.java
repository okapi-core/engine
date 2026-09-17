/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.traces.api;

import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.okapi.engine.api.TracesEngine;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@ConditionalOnProperty(
    name = "okapi.traces.consumptionType",
    havingValue = "wal",
    matchIfMissing = true)
@RequiredArgsConstructor
public class OtelTracesController {

  private final TracesEngine tracesEngine;

  @PostMapping(
      path = "/v1/traces",
      consumes = {MediaType.APPLICATION_PROTOBUF_VALUE, MediaType.APPLICATION_OCTET_STREAM_VALUE})
  public ResponseEntity<Void> ingest(@RequestBody byte[] body) throws Exception {
    var otlpTraces = ExportTraceServiceRequest.parseFrom(body);
    tracesEngine.ingest(otlpTraces);
    return ResponseEntity.ok().build();
  }
}

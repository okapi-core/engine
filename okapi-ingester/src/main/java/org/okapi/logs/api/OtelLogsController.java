/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.logs.api;

import io.opentelemetry.proto.collector.logs.v1.ExportLogsServiceRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.okapi.engine.api.LogsEngine;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@ConditionalOnProperty(
    name = "okapi.logs.consumptionType",
    havingValue = "wal",
    matchIfMissing = true)
@RequiredArgsConstructor
public class OtelLogsController {

  private final LogsEngine logsEngine;

  @PostMapping(
      path = "/v1/logs",
      consumes = {MediaType.APPLICATION_PROTOBUF_VALUE, MediaType.APPLICATION_OCTET_STREAM_VALUE})
  public ResponseEntity<Void> ingestProtobuf(@RequestBody byte[] body) throws Exception {
    var req = ExportLogsServiceRequest.parseFrom(body);
    logsEngine.ingest(req);
    return ResponseEntity.ok().build();
  }
}

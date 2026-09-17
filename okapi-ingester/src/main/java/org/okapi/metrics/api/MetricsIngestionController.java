/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.metrics.api;

import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.okapi.engine.api.MetricsEngine;
import org.okapi.metrics.otel.ConversionConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@ConditionalOnProperty(
    name = "okapi.metrics.consumptionType",
    havingValue = "wal",
    matchIfMissing = true)
@RequiredArgsConstructor
public class MetricsIngestionController {
  private final MetricsEngine metricsEngine;

  @PostMapping(
      path = "/v1/metrics",
      consumes = {MediaType.APPLICATION_PROTOBUF_VALUE, MediaType.APPLICATION_OCTET_STREAM_VALUE})
  public ResponseEntity<Void> ingest(@RequestBody byte[] body) throws Exception {
    var otlpMetrics = ExportMetricsServiceRequest.parseFrom(body);
    metricsEngine.ingest(otlpMetrics, ConversionConfig.noOp());
    return ResponseEntity.ok().build();
  }

  @PostMapping(
      path = "/prometheus/v1/metrics",
      consumes = {MediaType.APPLICATION_PROTOBUF_VALUE, MediaType.APPLICATION_OCTET_STREAM_VALUE})
  public ResponseEntity<Void> ingestPrometheus(@RequestBody byte[] body) throws Exception {
    var otlpMetrics = ExportMetricsServiceRequest.parseFrom(body);
    metricsEngine.ingest(otlpMetrics, ConversionConfig.prometheus());
    return ResponseEntity.ok().build();
  }
}

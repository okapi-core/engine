/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.traces.ch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import org.junit.jupiter.api.Test;
import org.okapi.exceptions.BadRequestException;

class ChTracesIngesterConsumptionTests {
  @Test
  void rejectsDirectIngestionInKafkaMode() {
    var ingester = new ChTracesIngester(null, false);

    var error =
        assertThrows(
            BadRequestException.class,
            () -> ingester.ingest(ExportTraceServiceRequest.getDefaultInstance()));

    assertEquals(
        "Direct traces ingestion is disabled because this server is running in Kafka consumption mode",
        error.getMessage());
  }
}

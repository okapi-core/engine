/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.logs.ch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.opentelemetry.proto.collector.logs.v1.ExportLogsServiceRequest;
import org.junit.jupiter.api.Test;
import org.okapi.exceptions.BadRequestException;

class ChLogsIngesterConsumptionTests {
  @Test
  void rejectsDirectIngestionInKafkaMode() {
    var ingester = new ChLogsIngester(null, false);

    var error =
        assertThrows(
            BadRequestException.class,
            () -> ingester.ingest(ExportLogsServiceRequest.getDefaultInstance()));

    assertEquals(
        "Direct logs ingestion is disabled because this server is running in Kafka consumption mode",
        error.getMessage());
  }
}

/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.metrics.ch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import org.junit.jupiter.api.Test;
import org.okapi.exceptions.BadRequestException;
import org.okapi.metrics.otel.ConversionConfig;

class ChMetricsIngesterConsumptionTests {
  @Test
  void rejectsDirectIngestionInKafkaMode() {
    var ingester = new ChMetricsIngester(null, null, false);

    var error =
        assertThrows(
            BadRequestException.class,
            () ->
                ingester.ingestOtelProtobuf(
                    ExportMetricsServiceRequest.getDefaultInstance(), ConversionConfig.noOp()));

    assertEquals(
        "Direct metrics ingestion is disabled because this server is running in Kafka consumption mode",
        error.getMessage());
  }
}

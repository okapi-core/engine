/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.it;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.proto.collector.logs.v1.ExportLogsServiceRequest;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.okapi.logs.api.OtelLogsController;
import org.okapi.metrics.api.MetricsIngestionController;
import org.okapi.spring.configs.MeteringConfiguration;
import org.okapi.spring.configs.OkapiHttpMetricsInterceptor;
import org.okapi.spring.configs.OkapiWebMvcConfiguration;
import org.okapi.traces.api.OtelTracesController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;

@SpringBootTest(
    classes = IngestionControllersKafkaModeIT.TestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "okapi.metrics.consumptionType=kafka",
      "okapi.logs.consumptionType=kafka",
      "okapi.traces.consumptionType=kafka"
    })
class IngestionControllersKafkaModeIT {
  @LocalServerPort private int port;
  @Autowired private MeterRegistry meterRegistry;

  private final HttpClient httpClient = HttpClient.newHttpClient();

  @Test
  void disablesMetricsIngestionEndpointsInKafkaMode() throws Exception {
    var payload = ExportMetricsServiceRequest.getDefaultInstance().toByteArray();

    assertNotFound("/v1/metrics", payload);
    assertNotFound("/prometheus/v1/metrics", payload);
  }

  @Test
  void disablesLogsIngestionEndpointInKafkaMode() throws Exception {
    var payload = ExportLogsServiceRequest.getDefaultInstance().toByteArray();

    assertNotFound("/v1/logs", payload);
  }

  @Test
  void disablesTracesIngestionEndpointInKafkaMode() throws Exception {
    var payload = ExportTraceServiceRequest.getDefaultInstance().toByteArray();

    assertNotFound("/v1/traces", payload);
  }

  @Test
  void recordsHttpMetricsForDisabledIngestion() throws Exception {
    var payload = ExportLogsServiceRequest.getDefaultInstance().toByteArray();
    var requestsBefore =
        meterRegistry
            .get("okapi.http.server.requests")
            .tag("method", "POST")
            .tag("outcome", "client_error")
            .counter()
            .count();

    assertNotFound("/v1/logs", payload);

    assertEquals(
        requestsBefore + 1.0,
        meterRegistry
            .get("okapi.http.server.requests")
            .tag("method", "POST")
            .tag("outcome", "client_error")
            .counter()
            .count());
    assertEquals(0.0, meterRegistry.get("okapi.http.server.in_flight").gauge().value());
  }

  private void assertNotFound(String path, byte[] payload) throws Exception {
    var request =
        HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + path))
            .header("Content-Type", MediaType.APPLICATION_PROTOBUF_VALUE)
            .POST(HttpRequest.BodyPublishers.ofByteArray(payload))
            .build();
    var response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());

    assertEquals(404, response.statusCode());
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @Import({
    MetricsIngestionController.class,
    OtelLogsController.class,
    OtelTracesController.class,
    MeteringConfiguration.class,
    OkapiHttpMetricsInterceptor.class,
    OkapiWebMvcConfiguration.class
  })
  static class TestApplication {}
}

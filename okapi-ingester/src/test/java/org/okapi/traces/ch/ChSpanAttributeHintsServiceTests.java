/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.traces.ch;

import static org.junit.jupiter.api.Assertions.*;
import static org.okapi.otelshorthand.OtelShortHands.keyValue;
import static org.okapi.otelshorthand.OtelShortHands.utf8Bytes;

import com.clickhouse.client.api.Client;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.protobuf.ByteString;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.resource.v1.Resource;
import io.opentelemetry.proto.trace.v1.ResourceSpans;
import io.opentelemetry.proto.trace.v1.ScopeSpans;
import io.opentelemetry.proto.trace.v1.Span;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.okapi.ch.CreateChTablesSpec;
import org.okapi.otelshorthand.OtelShortHands;
import org.okapi.rest.traces.SpanAttributeHint;
import org.okapi.rest.traces.SpanAttributeHintsRequest;
import org.okapi.rest.traces.SpanAttributeHintsResponse;
import org.okapi.rest.traces.SpanAttributeValueHintsRequest;
import org.okapi.rest.traces.SpanAttributeValueHintsResponse;
import org.okapi.rest.traces.TimestampMillisFilter;
import org.okapi.testmodules.guice.TestChTracesModule;
import org.okapi.traces.core.FakeTracesEventEmitter;
import org.okapi.traces.core.TracesEvent;

public class ChSpanAttributeHintsServiceTests {

  @TempDir Path tempDir;

  private Injector injector;
  private Client client;

  @BeforeEach
  void setup() {
    injector = Guice.createInjector(new TestChTracesModule(tempDir.resolve("wal"), 16));
    client = injector.getInstance(Client.class);
    CreateChTablesSpec.migrate(client);
    truncateTables();
  }

  @Test
  void attributeHintsTest() throws Exception {
    ingestCorpus();
    checkAttributeHints();
    checkDefaultAttributesAlwaysPresent();
    checkCustomStringValues();
    checkCustomNumericSummary();
    checkDefaultStringValues();
    checkDefaultNumericSummary();
  }

  private void ingestCorpus() throws Exception {
    var emitter = injector.getInstance(FakeTracesEventEmitter.class);
    var driver = injector.getInstance(ChTracesWalConsumerDriver.class);

    var traceIdA = utf8Bytes("trace-hints-0001");
    var spanIdA = utf8Bytes("span-hints-0001");
    var traceIdB = utf8Bytes("trace-hints-0002");
    var spanIdB = utf8Bytes("span-hints-0002");

    var request =
        ExportTraceServiceRequest.newBuilder()
            .addResourceSpans(buildSpan(traceIdA, spanIdA, 1_000_000_000L, "alpha", 10, "GET", 200))
            .addResourceSpans(buildSpan(traceIdB, spanIdB, 2_000_000_000L, "beta", 20, "POST", 404))
            .build();

    emitter.add(new TracesEvent(request.toByteArray()));
    driver.onTick();
  }

  private void checkAttributeHints() {
    var service = injector.getInstance(ChSpanAttributeHintsService.class);
    var request =
        SpanAttributeHintsRequest.builder()
            .timestampFilter(
                TimestampMillisFilter.builder().tsMillisStart(0).tsMillisEnd(10_000).build())
            .build();
    SpanAttributeHintsResponse response = service.getAttributeHints(request);
    assertNotNull(response);
    assertTrue(hasHint(response.getDefaultAttributes(), "service_name", "string"));
    assertTrue(hasHint(response.getDefaultAttributes(), "http_status_code", "number"));
    assertTrue(hasHint(response.getCustomAttributes(), "custom.string", "string"));
    assertTrue(hasHint(response.getCustomAttributes(), "custom.number", "number"));
  }

  private void checkDefaultAttributesAlwaysPresent() {
    var service = injector.getInstance(ChSpanAttributeHintsService.class);
    var request =
        SpanAttributeHintsRequest.builder()
            .timestampFilter(
                TimestampMillisFilter.builder().tsMillisStart(100_000).tsMillisEnd(200_000).build())
            .build();
    SpanAttributeHintsResponse response = service.getAttributeHints(request);
    assertNotNull(response);
    assertTrue(hasHint(response.getDefaultAttributes(), "service_name", "string"));
    assertTrue(hasHint(response.getDefaultAttributes(), "http_status_code", "number"));
  }

  private void checkCustomStringValues() {
    var service = injector.getInstance(ChSpanAttributeHintsService.class);
    var request =
        SpanAttributeValueHintsRequest.builder()
            .attributeName("custom.string")
            .timestampFilter(
                TimestampMillisFilter.builder().tsMillisStart(0).tsMillisEnd(10_000).build())
            .build();
    SpanAttributeValueHintsResponse response = service.getAttributeValueHints(request);
    assertNotNull(response);
    assertTrue(response.getValues().contains("alpha"));
    assertTrue(response.getValues().contains("beta"));
    assertNull(response.getNumericSummary());
  }

  private void checkCustomNumericSummary() {
    var service = injector.getInstance(ChSpanAttributeHintsService.class);
    var request =
        SpanAttributeValueHintsRequest.builder()
            .attributeName("custom.number")
            .timestampFilter(
                TimestampMillisFilter.builder().tsMillisStart(0).tsMillisEnd(10_000).build())
            .build();
    SpanAttributeValueHintsResponse response = service.getAttributeValueHints(request);
    assertNotNull(response);
    assertNotNull(response.getNumericSummary());
    assertEquals(15.0, response.getNumericSummary().getAvg(), 0.0001);
    assertTrue(response.getValues().isEmpty());
  }

  private void checkDefaultStringValues() {
    var service = injector.getInstance(ChSpanAttributeHintsService.class);
    var request =
        SpanAttributeValueHintsRequest.builder()
            .attributeName("http_method")
            .timestampFilter(
                TimestampMillisFilter.builder().tsMillisStart(0).tsMillisEnd(10_000).build())
            .build();
    SpanAttributeValueHintsResponse response = service.getAttributeValueHints(request);
    assertNotNull(response);
    assertTrue(response.getValues().contains("GET"));
    assertTrue(response.getValues().contains("POST"));
    assertNull(response.getNumericSummary());
  }

  private void checkDefaultNumericSummary() {
    var service = injector.getInstance(ChSpanAttributeHintsService.class);
    var request =
        SpanAttributeValueHintsRequest.builder()
            .attributeName("http_status_code")
            .timestampFilter(
                TimestampMillisFilter.builder().tsMillisStart(0).tsMillisEnd(10_000).build())
            .build();
    SpanAttributeValueHintsResponse response = service.getAttributeValueHints(request);
    assertNotNull(response);
    assertNotNull(response.getNumericSummary());
    assertEquals(302.0, response.getNumericSummary().getAvg(), 0.0001);
  }

  private void truncateTables() {
    client.queryAll("TRUNCATE TABLE okapi_traces.spans_table_v1");
    client.queryAll("TRUNCATE TABLE okapi_traces.spans_ingested_attribs");
  }

  private ResourceSpans buildSpan(
      ByteString traceId,
      ByteString spanId,
      long tsStart,
      String customString,
      int customNumber,
      String httpMethod,
      int statusCode) {
    var resource =
        Resource.newBuilder().addAttributes(keyValue("service.name", "svc-hints")).build();
    var span =
        Span.newBuilder()
            .setTraceId(traceId)
            .setSpanId(spanId)
            .setName("hints-span")
            .setKind(Span.SpanKind.SPAN_KIND_SERVER)
            .setStartTimeUnixNano(tsStart)
            .setEndTimeUnixNano(tsStart + 100_000_000L)
            .addAttributes(keyValue("custom.string", customString))
            .addAttributes(OtelShortHands.keyValue("custom.number", customNumber))
            .addAttributes(keyValue("http.request.method", httpMethod))
            .addAttributes(OtelShortHands.keyValue("http.response.status_code", statusCode))
            .build();
    var scope = ScopeSpans.newBuilder().addSpans(span).build();
    return ResourceSpans.newBuilder().setResource(resource).addScopeSpans(scope).build();
  }

  private boolean hasHint(List<SpanAttributeHint> hints, String name, String type) {
    for (var hint : hints) {
      if (name.equals(hint.getName()) && type.equals(hint.getType())) return true;
    }
    return false;
  }
}

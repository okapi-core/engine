/*
 * Copyright The OkapiCore Authors
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.okapi.ch.CreateChTablesSpec;
import org.okapi.metrics.pojos.AGG_TYPE;
import org.okapi.metrics.pojos.RES_TYPE;
import org.okapi.otelshorthand.OtelShortHands;
import org.okapi.rest.traces.*;
import org.okapi.testmodules.guice.TestChTracesModule;
import org.okapi.traces.core.FakeTracesEventEmitter;
import org.okapi.traces.core.TracesEvent;

@TestInstance(Lifecycle.PER_CLASS)
public class ChSpanStatsQueryServiceTests {

  private Injector injector;
  private Client client;

  @BeforeAll
  void setup() throws Exception {
    Path tempDir = Files.createTempDirectory("okapi-traces-stats-");
    injector = Guice.createInjector(new TestChTracesModule(tempDir.resolve("wal"), 16));
    client = injector.getInstance(Client.class);
    CreateChTablesSpec.migrate(client);
    truncateTables();
    ingestCorpus();
  }

  private void ingestCorpus() throws Exception {
    var emitter = injector.getInstance(FakeTracesEventEmitter.class);
    var driver = injector.getInstance(ChTracesWalConsumerDriver.class);

    var traceIdA = utf8Bytes("trace-stats-0001");
    var spanIdA = utf8Bytes("span-stats-0001");
    var traceIdB = utf8Bytes("trace-stats-0002");
    var spanIdB = utf8Bytes("span-stats-0002");
    var traceIdC = utf8Bytes("trace-stats-0003");
    var spanIdC = utf8Bytes("span-stats-0003");

    var traceIdD = utf8Bytes("span-stats-0004");
    var spanIdD = utf8Bytes("span-stats-0004");

    var request =
        ExportTraceServiceRequest.newBuilder()
            .addResourceSpans(buildStatsSpan(traceIdA, spanIdA, 1_000_000_000L, "alpha", 10))
            .addResourceSpans(buildStatsSpan(traceIdB, spanIdB, 2_000_000_000L, "beta", 20))
            .addResourceSpans(buildStatsSpan(traceIdC, spanIdC, 3_000_000_000L, null, 30))
            .addResourceSpans(buildDefaultAttributeSpan(traceIdD, spanIdD, 4_000_000_000L))
            .build();

    emitter.add(new TracesEvent(request.toByteArray()));
    driver.onTick();
  }

  @ParameterizedTest
  @MethodSource("customNumericResolutionCases")
  public void checkNumericAggregation(RES_TYPE resType, List<NumericPoint> expectedPoints) {
    var statsService = injector.getInstance(ChSpanStatsQueryService.class);
    var request =
        SpansQueryStatsRequest.builder()
            .attributes(List.of("custom.number"))
            .numericalAgg(
                NumericalAggConfig.builder().aggregation(AGG_TYPE.AVG).resType(resType).build())
            .timestampFilter(
                TimestampFilter.builder().tsStartNanos(0).tsEndNanos(10_000_000_000L).build())
            .build();
    SpansQueryStatsResponse response = statsService.getStats(request);
    assertNotNull(response);
    assertTrue(response.getCount() > 0);
    AttributeNumericSeries series = findNumericSeries(response, "custom.number");
    assertNotNull(series);
    assertEquals(expectedPoints, series.getPoints());
  }

  @Test
  public void checkStringAggregation() {
    var statsService = injector.getInstance(ChSpanStatsQueryService.class);
    var request =
        SpansQueryStatsRequest.builder()
            .attributes(List.of("custom.string"))
            .timestampFilter(
                TimestampFilter.builder().tsStartNanos(0).tsEndNanos(10_000_000_000L).build())
            .build();
    SpansQueryStatsResponse response = statsService.getStats(request);
    assertNotNull(response);
    AttributeDistributionSummary summary = findDistributionSummary(response, "custom.string");
    assertNotNull(summary);
    var values = summary.getValues().stream().map(ValueCount::getValue).toList();
    assertTrue(values.contains("alpha"));
    assertTrue(values.contains("beta"));
  }

  @Test
  public void checkMissingAttributeDoesNotCrash() {
    var statsService = injector.getInstance(ChSpanStatsQueryService.class);
    var request =
        SpansQueryStatsRequest.builder()
            .attributes(List.of("custom.missing"))
            .timestampFilter(
                TimestampFilter.builder().tsStartNanos(0).tsEndNanos(10_000_000_000L).build())
            .build();
    SpansQueryStatsResponse response = statsService.getStats(request);
    assertNotNull(response);
    assertNull(findDistributionSummary(response, "custom.missing"));
    assertNull(findNumericSeries(response, "custom.missing"));
  }

  @Test
  public void checkEmptyResponseForNoMatches() {
    var statsService = injector.getInstance(ChSpanStatsQueryService.class);
    var request =
        SpansQueryStatsRequest.builder()
            .attributes(List.of("custom.number"))
            .serviceFilter(ServiceFilter.builder().service("svc-missing").build())
            .numericalAgg(
                NumericalAggConfig.builder()
                    .aggregation(AGG_TYPE.AVG)
                    .resType(RES_TYPE.HOURLY)
                    .build())
            .timestampFilter(
                TimestampFilter.builder().tsStartNanos(0).tsEndNanos(10_000_000_000L).build())
            .build();
    SpansQueryStatsResponse response = statsService.getStats(request);
    assertNotNull(response);
    assertEquals(0L, response.getCount());
    assertTrue(response.getNumericSeries().getFirst().getPoints().isEmpty());
    assertNull(response.getDistributionSummaries());
  }

  @ParameterizedTest
  @MethodSource("defaultNumericResolutionCases")
  public void checkDefaultAggregation(RES_TYPE resType, List<NumericPoint> expectedPoints) {
    var statsService = injector.getInstance(ChSpanStatsQueryService.class);
    var request =
        SpansQueryStatsRequest.builder()
            .attributes(List.of("http_request_size"))
            .serviceFilter(ServiceFilter.builder().service("svc-stats").build())
            .numericalAgg(
                NumericalAggConfig.builder().aggregation(AGG_TYPE.AVG).resType(resType).build())
            .timestampFilter(
                TimestampFilter.builder().tsStartNanos(0).tsEndNanos(10_000_000_000L).build())
            .build();
    SpansQueryStatsResponse response = statsService.getStats(request);
    assertNotNull(response);
    var series = response.getNumericSeries().getFirst();
    assertEquals(expectedPoints, series.getPoints());
    assertNull(response.getDistributionSummaries());
  }

  private void truncateTables() {
    client.queryAll("TRUNCATE TABLE okapi_traces.spans_table_v1");
    client.queryAll("TRUNCATE TABLE okapi_traces.spans_ingested_attribs");
  }

  private ResourceSpans buildStatsSpan(
      ByteString traceId, ByteString spanId, long tsStart, String stringValue, int numberValue) {
    var resource =
        Resource.newBuilder().addAttributes(keyValue("service.name", "svc-stats")).build();
    var spanBuilder =
        Span.newBuilder()
            .setTraceId(traceId)
            .setSpanId(spanId)
            .setName("stats-span")
            .setKind(Span.SpanKind.SPAN_KIND_SERVER)
            .setStartTimeUnixNano(tsStart)
            .setEndTimeUnixNano(tsStart + 100_000_000L)
            .addAttributes(OtelShortHands.keyValue("custom.number", numberValue));
    if (stringValue != null) {
      spanBuilder.addAttributes(keyValue("custom.string", stringValue));
    }
    var scope = ScopeSpans.newBuilder().addSpans(spanBuilder.build()).build();
    return ResourceSpans.newBuilder().setResource(resource).addScopeSpans(scope).build();
  }

  private ResourceSpans buildDefaultAttributeSpan(
      ByteString traceId, ByteString spanId, long tsStart) {
    var resource =
        Resource.newBuilder().addAttributes(keyValue("service.name", "svc-stats")).build();
    var spanBuilder =
        Span.newBuilder()
            .setTraceId(traceId)
            .setSpanId(spanId)
            .setName("stats-span")
            .setKind(Span.SpanKind.SPAN_KIND_SERVER)
            .setStartTimeUnixNano(tsStart)
            .setEndTimeUnixNano(tsStart + 100_000_000L)
            .addAttributes(OtelShortHands.keyValue("http.request.body.size", 100));
    var scope = ScopeSpans.newBuilder().addSpans(spanBuilder.build()).build();
    return ResourceSpans.newBuilder().setResource(resource).addScopeSpans(scope).build();
  }

  private AttributeNumericSeries findNumericSeries(
      SpansQueryStatsResponse response, String attribute) {
    if (response.getNumericSeries() == null) {
      return null;
    }
    for (var series : response.getNumericSeries()) {
      if (attribute.equals(series.getAttribute())) return series;
    }
    return null;
  }

  private static Stream<Arguments> customNumericResolutionCases() {
    return Stream.of(
        Arguments.of(
            RES_TYPE.HOURLY, List.of(NumericPoint.builder().bucketStartMs(0L).value(20.0).build())),
        Arguments.of(
            RES_TYPE.MINUTELY,
            List.of(NumericPoint.builder().bucketStartMs(0L).value(20.0).build())),
        Arguments.of(
            RES_TYPE.SECONDLY,
            List.of(
                NumericPoint.builder().bucketStartMs(1_000L).value(10.0).build(),
                NumericPoint.builder().bucketStartMs(2_000L).value(20.0).build(),
                NumericPoint.builder().bucketStartMs(3_000L).value(30.0).build())));
  }

  private static Stream<Arguments> defaultNumericResolutionCases() {
    return Stream.of(
        Arguments.of(
            RES_TYPE.HOURLY,
            List.of(NumericPoint.builder().bucketStartMs(0L).value(100.0).build())),
        Arguments.of(
            RES_TYPE.MINUTELY,
            List.of(NumericPoint.builder().bucketStartMs(0L).value(100.0).build())),
        Arguments.of(
            RES_TYPE.SECONDLY,
            List.of(NumericPoint.builder().bucketStartMs(4_000L).value(100.0).build())));
  }

  private AttributeDistributionSummary findDistributionSummary(
      SpansQueryStatsResponse response, String attribute) {
    if (response.getDistributionSummaries() == null) {
      return null;
    }
    for (var summary : response.getDistributionSummaries()) {
      if (attribute.equals(summary.getAttribute())) return summary;
    }
    return null;
  }
}

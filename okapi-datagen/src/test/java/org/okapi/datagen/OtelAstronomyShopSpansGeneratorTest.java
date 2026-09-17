/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.datagen;

import static org.junit.jupiter.api.Assertions.*;

import io.opentelemetry.proto.collector.logs.v1.ExportLogsServiceRequest;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.logs.v1.LogRecord;
import io.opentelemetry.proto.trace.v1.Span;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.okapi.datagen.spans.*;

public class OtelAstronomyShopSpansGeneratorTest {

  @Test
  void generate_isDeterministicWithSeed() {
    var config =
        SpansGeneratorConfig.builder()
            .seed(123L)
            .traceCount(2)
            .baseStartMs(1_000L)
            .traceSpacingMs(10L)
            .stepGapMs(1L)
            .childOffsetMs(1L)
            .childGapMs(1L)
            .tailMs(1L)
            .journey(Journey.defaultJourney())
            .build();
    var genA = new OtelAstronomyShopSpansGenerator(config);
    var genB = new OtelAstronomyShopSpansGenerator(config);

    var outA = genA.generate();
    var outB = genB.generate();

    assertEquals(outA.size(), outB.size());
    for (int i = 0; i < outA.size(); i++) {
      assertArrayEquals(outA.get(i).toByteArray(), outB.get(i).toByteArray());
    }
  }

  @Test
  void generateTelemetry_isDeterministicWithSeed() {
    var config =
        SpansGeneratorConfig.builder()
            .seed(123L)
            .traceCount(2)
            .baseStartMs(1_000L)
            .traceSpacingMs(10L)
            .stepGapMs(1L)
            .childOffsetMs(1L)
            .childGapMs(1L)
            .tailMs(1L)
            .journey(Journey.defaultJourney())
            .build();
    var genA = new OtelAstronomyShopSpansGenerator(config);
    var genB = new OtelAstronomyShopSpansGenerator(config);

    var outA = genA.generateTelemetry();
    var outB = genB.generateTelemetry();

    assertEquals(outA.getTraces().size(), outB.getTraces().size());
    assertEquals(outA.getLogs().size(), outB.getLogs().size());
    for (int i = 0; i < outA.getTraces().size(); i++) {
      assertArrayEquals(
          outA.getTraces().get(i).toByteArray(), outB.getTraces().get(i).toByteArray());
    }
    for (int i = 0; i < outA.getLogs().size(); i++) {
      assertArrayEquals(outA.getLogs().get(i).toByteArray(), outB.getLogs().get(i).toByteArray());
    }
  }

  @Test
  void generate_emitsOneRequestPerService() {
    var config =
        SpansGeneratorConfig.builder()
            .seed(1L)
            .traceCount(1)
            .baseStartMs(1_000L)
            .traceSpacingMs(10L)
            .stepGapMs(1L)
            .childOffsetMs(1L)
            .childGapMs(1L)
            .tailMs(1L)
            .journey(Journey.defaultJourney())
            .build();
    var generator = new OtelAstronomyShopSpansGenerator(config);
    var out = generator.generate();

    var services =
        out.stream()
            .flatMap(req -> req.getResourceSpansList().stream())
            .map(rs -> rs.getResource().getAttributesList())
            .flatMap(List::stream)
            .filter(kv -> kv.getKey().equals("service.name"))
            .map(kv -> kv.getValue().getStringValue())
            .collect(Collectors.toSet());

    assertTrue(out.size() >= services.size());
    assertTrue(services.contains("frontend"));
    assertTrue(services.contains("payment-service"));
  }

  @Test
  void generate_hasParentChildRelationships() {
    var config =
        SpansGeneratorConfig.builder()
            .seed(2L)
            .traceCount(1)
            .baseStartMs(1_000L)
            .traceSpacingMs(10L)
            .stepGapMs(1L)
            .childOffsetMs(1L)
            .childGapMs(1L)
            .tailMs(1L)
            .journey(Journey.defaultJourney())
            .build();
    var generator = new OtelAstronomyShopSpansGenerator(config);
    var out = generator.generate();
    var spans = allSpans(out);

    var spanById = spans.stream().collect(Collectors.toMap(Span::getSpanId, s -> s, (a, b) -> a));

    boolean hasParent = spans.stream().anyMatch(span -> !span.getParentSpanId().isEmpty());
    boolean parentExists =
        spans.stream()
            .filter(span -> !span.getParentSpanId().isEmpty())
            .allMatch(span -> spanById.containsKey(span.getParentSpanId()));

    assertTrue(hasParent);
    assertTrue(parentExists);
  }

  @Test
  void generate_errorOnlyStateSetsErrorAttributes() {
    var errorState =
        ComponentState.builder()
            .successRate(0.0)
            .errorRates(Map.of(ErrorType.TIMEOUT, 1.0))
            .errorMessages(Map.of(ErrorType.TIMEOUT, "forced timeout"))
            .latency(LatencyConfig.builder().minMs(1).maxMs(1).timeoutPenaltyMs(1).build())
            .build();
    var systemState =
        SystemState.builder()
            .name("error")
            .weight(1.0)
            .components(Map.of("frontend", errorState))
            .build();
    var journey =
        Journey.builder()
            .rootStep(Step.builder().spanName("frontend/purchase").component("frontend").build())
            .build();
    var config =
        SpansGeneratorConfig.builder()
            .seed(7L)
            .traceCount(1)
            .baseStartMs(1_000L)
            .traceSpacingMs(10L)
            .stepGapMs(1L)
            .childOffsetMs(1L)
            .childGapMs(1L)
            .tailMs(1L)
            .defaultComponentState(errorState)
            .states(List.of(systemState))
            .journey(journey)
            .build();
    var generator = new OtelAstronomyShopSpansGenerator(config);
    var out = generator.generate();
    var spans = allSpans(out);

    var span = spans.get(0);
    var attrs =
        span.getAttributesList().stream()
            .collect(Collectors.toMap(kv -> kv.getKey(), kv -> kv.getValue(), (a, b) -> a));

    assertEquals("timeout", attrs.get("error.type").getStringValue());
    assertEquals("forced timeout", attrs.get("error.message").getStringValue());
    assertEquals(504, attrs.get("http.status_code").getIntValue());
  }

  @Test
  void generateTelemetry_emitsLogsCorrelatedToSpans() {
    var config =
        SpansGeneratorConfig.builder()
            .seed(2L)
            .traceCount(1)
            .baseStartMs(1_000L)
            .traceSpacingMs(10L)
            .stepGapMs(1L)
            .childOffsetMs(1L)
            .childGapMs(1L)
            .tailMs(1L)
            .journey(Journey.defaultJourney())
            .build();
    var generator = new OtelAstronomyShopSpansGenerator(config);
    var telemetry = generator.generateTelemetry();
    var spans = allSpans(telemetry.getTraces());
    var logs = allLogs(telemetry.getLogs());

    var spanRefs =
        spans.stream()
            .map(span -> span.getTraceId().toStringUtf8() + ":" + span.getSpanId().toStringUtf8())
            .collect(Collectors.toSet());
    var logRefs =
        logs.stream()
            .map(log -> log.getTraceId().toStringUtf8() + ":" + log.getSpanId().toStringUtf8())
            .collect(Collectors.toSet());

    assertEquals(spans.size(), logs.size());
    assertEquals(spanRefs, logRefs);
    assertTrue(logs.stream().allMatch(log -> log.getSeverityText().equals("INFO")));
    assertTrue(logs.stream().allMatch(log -> !log.getBody().getStringValue().isEmpty()));
  }

  @Test
  void generateTelemetry_errorLogsHaveSearchableErrorFields() {
    var errorState =
        ComponentState.builder()
            .successRate(0.0)
            .errorRates(Map.of(ErrorType.TIMEOUT, 1.0))
            .errorMessages(Map.of(ErrorType.TIMEOUT, "forced timeout"))
            .latency(LatencyConfig.builder().minMs(1).maxMs(1).timeoutPenaltyMs(1).build())
            .build();
    var systemState =
        SystemState.builder()
            .name("error")
            .weight(1.0)
            .components(Map.of("frontend", errorState))
            .build();
    var journey =
        Journey.builder()
            .rootStep(Step.builder().spanName("frontend/purchase").component("frontend").build())
            .build();
    var config =
        SpansGeneratorConfig.builder()
            .seed(7L)
            .traceCount(1)
            .baseStartMs(1_000L)
            .traceSpacingMs(10L)
            .stepGapMs(1L)
            .childOffsetMs(1L)
            .childGapMs(1L)
            .tailMs(1L)
            .defaultComponentState(errorState)
            .states(List.of(systemState))
            .journey(journey)
            .build();
    var generator = new OtelAstronomyShopSpansGenerator(config);
    var log = allLogs(generator.generateTelemetry().getLogs()).get(0);
    var attrs =
        log.getAttributesList().stream()
            .collect(Collectors.toMap(kv -> kv.getKey(), kv -> kv.getValue(), (a, b) -> a));

    assertEquals(17, log.getSeverityNumberValue());
    assertEquals("ERROR", log.getSeverityText());
    assertTrue(log.getBody().getStringValue().contains("forced timeout"));
    assertEquals("frontend", attrs.get("log.stream").getStringValue());
    assertEquals("timeout", attrs.get("error.type").getStringValue());
    assertEquals("forced timeout", attrs.get("error.message").getStringValue());
    assertEquals("error", attrs.get("system.state").getStringValue());
    assertEquals(504, attrs.get("http.status_code").getIntValue());
  }

  private static List<Span> allSpans(List<ExportTraceServiceRequest> out) {
    return out.stream()
        .flatMap(req -> req.getResourceSpansList().stream())
        .flatMap(rs -> rs.getScopeSpansList().stream())
        .flatMap(ss -> ss.getSpansList().stream())
        .toList();
  }

  private static List<LogRecord> allLogs(List<ExportLogsServiceRequest> out) {
    return out.stream()
        .flatMap(req -> req.getResourceLogsList().stream())
        .flatMap(rs -> rs.getScopeLogsList().stream())
        .flatMap(sl -> sl.getLogRecordsList().stream())
        .toList();
  }
}

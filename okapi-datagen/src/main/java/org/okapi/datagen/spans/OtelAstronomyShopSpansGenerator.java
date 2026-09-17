/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.datagen.spans;

import static org.okapi.random.RandomUtils.*;

import io.opentelemetry.proto.collector.logs.v1.ExportLogsServiceRequest;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.logs.v1.LogRecord;
import io.opentelemetry.proto.logs.v1.ResourceLogs;
import io.opentelemetry.proto.logs.v1.ScopeLogs;
import io.opentelemetry.proto.resource.v1.Resource;
import io.opentelemetry.proto.trace.v1.ResourceSpans;
import io.opentelemetry.proto.trace.v1.ScopeSpans;
import io.opentelemetry.proto.trace.v1.Span;
import io.opentelemetry.proto.trace.v1.Status;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import org.apache.commons.math3.distribution.LogNormalDistribution;
import org.apache.commons.math3.random.MersenneTwister;
import org.okapi.random.RandomUtils;
import org.okapi.timeutils.TimeUtils;

public class OtelAstronomyShopSpansGenerator {
  private static final int SEVERITY_INFO = 9;
  private static final int SEVERITY_WARN = 13;
  private static final int SEVERITY_ERROR = 17;

  private final SpansGeneratorConfig config;
  private final Random random;
  private final LogNormalDistribution requestSizes;

  public OtelAstronomyShopSpansGenerator(SpansGeneratorConfig config) {
    this.config = config;
    this.random = new Random(config.getSeed());
    var generator = new MersenneTwister(config.getSeed());
    this.requestSizes = new LogNormalDistribution(generator, 0., 1.);
  }

  public List<ExportTraceServiceRequest> generate() {
    return generateTelemetry().getTraces();
  }

  public List<ExportLogsServiceRequest> generateLogs() {
    return generateTelemetry().getLogs();
  }

  public AstronomyShopTelemetry generateTelemetry() {
    var traceRequests = new ArrayList<ExportTraceServiceRequest>();
    var logRequests = new ArrayList<ExportLogsServiceRequest>();
    for (int i = 0; i < config.getTraceCount(); i++) {
      var state = selectRandomState(config.getStates());
      long traceStartNs =
          TimeUtils.millisToNanos(config.getBaseStartMs() + (i * config.getTraceSpacingMs()));
      byte[] traceId = randomOtelTraceId(random);
      var spansByService = new HashMap<String, List<Span>>();
      var logsByService = new HashMap<String, List<LogRecord>>();
      var ctx = new TraceContext(state, traceId, spansByService, logsByService);
      long currentStart = traceStartNs;
      for (var step : config.getJourney().getRootSteps()) {
        currentStart =
            simulateStep(step, ctx, null, currentStart)
                + TimeUtils.millisToNanos(config.getStepGapMs());
      }

      for (var entry : spansByService.entrySet()) {
        traceRequests.add(buildTraceRequest(entry.getKey(), entry.getValue()));
      }
      for (var entry : logsByService.entrySet()) {
        logRequests.add(buildLogsRequest(entry.getKey(), entry.getValue()));
      }
    }
    return new AstronomyShopTelemetry(traceRequests, logRequests);
  }

  private long simulateStep(Step step, TraceContext ctx, SpanRef parentRef, long startNs) {
    var componentState =
        ctx.getState().componentState(step.getComponent(), config.getDefaultComponentState());
    var outcome = componentState.sampleOutcome(random);
    long durationNs = componentState.sampleDurationNs(random, outcome);
    long endNs = startNs + durationNs;

    var spanId = getRanomOtelSpanId(random);
    var spanRef = new SpanRef(spanId, ctx.getTraceId());

    var childStart = startNs + TimeUtils.millisToNanos(config.getChildOffsetMs());
    boolean allowChildren = outcome == Outcome.SUCCESS || outcome == Outcome.DEPENDENCY_ERROR;

    if (allowChildren && !step.getChildren().isEmpty()) {
      for (var child : step.getChildren()) {
        long childEnd = simulateStep(child, ctx, spanRef, childStart);
        childStart = childEnd + TimeUtils.millisToNanos(config.getChildGapMs());
      }
      endNs = Math.max(endNs, childStart + TimeUtils.millisToNanos(config.getTailMs()));
    }

    String peerService = null;
    if (allowChildren && !step.getChildren().isEmpty()) {
      peerService = step.getChildren().get(0).getComponent();
    }
    var span =
        buildSpan(
            step,
            parentRef,
            spanRef,
            startNs,
            endNs,
            outcome,
            componentState.errorMessage(outcome),
            peerService);
    ctx.getSpansByService().computeIfAbsent(step.getComponent(), k -> new ArrayList<>()).add(span);
    ctx.getLogsByService()
        .computeIfAbsent(step.getComponent(), k -> new ArrayList<>())
        .add(
            buildLogRecord(
                step,
                spanRef,
                ctx.getState().getName(),
                startNs,
                outcome,
                componentState.errorMessage(outcome)));
    return endNs;
  }

  private Span buildSpan(
      Step step,
      SpanRef parentRef,
      SpanRef spanRef,
      long startNs,
      long endNs,
      Outcome outcome,
      String errorMessage,
      String peerService) {
    var builder =
        Span.newBuilder()
            .setTraceId(spanRef.traceIdBytes())
            .setSpanId(spanRef.spanIdBytes())
            .setName(step.getSpanName())
            .setKind(step.getKind())
            .setStartTimeUnixNano(startNs)
            .setEndTimeUnixNano(endNs)
            .addAllAttributes(step.getAttributes());
    if (parentRef != null) {
      builder.setParentSpanId(parentRef.spanIdBytes());
    }
    if (peerService != null && !peerService.isEmpty()) {
      builder.addAttributes(OtelShorthand.kv("peer.service", peerService));
    }
    if (outcome != Outcome.SUCCESS) {
      builder.setStatus(Status.newBuilder().setCode(Status.StatusCode.STATUS_CODE_ERROR).build());
      builder.addAttributes(OtelShorthand.kv("error.type", outcome.errorType));
      if (errorMessage != null && !errorMessage.isEmpty()) {
        builder.addAttributes(OtelShorthand.kv("error.message", errorMessage));
      }
      builder.addAttributes(OtelShorthand.kvInt("http.status_code", outcome.httpStatusCode));
    } else {
      builder.setStatus(Status.newBuilder().setCode(Status.StatusCode.STATUS_CODE_OK).build());
      var requestSize = this.requestSizes.sample();
      builder.addAttributes(OtelShorthand.kvDouble("http.server.request.body.size", requestSize));
      builder.addAttributes(OtelShorthand.kvDouble("http.request.body.size", requestSize));
      builder.addAttributes(OtelShorthand.kvInt("http.status_code", 200));
    }
    return builder.build();
  }

  private LogRecord buildLogRecord(
      Step step,
      SpanRef spanRef,
      String stateName,
      long timeNs,
      Outcome outcome,
      String errorMessage) {
    var severity = logSeverity(outcome);
    var builder =
        LogRecord.newBuilder()
            .setTimeUnixNano(timeNs)
            .setObservedTimeUnixNano(timeNs)
            .setTraceId(spanRef.traceIdBytes())
            .setSpanId(spanRef.spanIdBytes())
            .setSeverityNumberValue(severity)
            .setSeverityText(severityText(severity))
            .setBody(OtelShorthand.anyString(logBody(step, outcome, errorMessage)))
            .addAttributes(OtelShorthand.kv("log.stream", step.getComponent()))
            .addAttributes(OtelShorthand.kv("span.name", step.getSpanName()))
            .addAttributes(OtelShorthand.kv("component", step.getComponent()))
            .addAttributes(OtelShorthand.kv("system.state", stateName))
            .addAttributes(OtelShorthand.kvInt("http.status_code", outcome.httpStatusCode));
    if (outcome != Outcome.SUCCESS) {
      builder.addAttributes(OtelShorthand.kv("error.type", outcome.errorType));
      if (errorMessage != null && !errorMessage.isEmpty()) {
        builder.addAttributes(OtelShorthand.kv("error.message", errorMessage));
      }
    }
    return builder.build();
  }

  private int logSeverity(Outcome outcome) {
    return switch (outcome) {
      case SUCCESS -> SEVERITY_INFO;
      case DEPENDENCY_ERROR -> SEVERITY_WARN;
      case TIMEOUT, APP_ERROR, CRASH -> SEVERITY_ERROR;
    };
  }

  private String severityText(int severity) {
    return switch (severity) {
      case SEVERITY_WARN -> "WARN";
      case SEVERITY_ERROR -> "ERROR";
      default -> "INFO";
    };
  }

  private String logBody(Step step, Outcome outcome, String errorMessage) {
    if (outcome == Outcome.SUCCESS) {
      return step.getSpanName() + " completed successfully";
    }
    var message = errorMessage == null || errorMessage.isEmpty() ? outcome.errorType : errorMessage;
    return step.getSpanName() + " failed: " + message;
  }

  private ExportTraceServiceRequest buildTraceRequest(String serviceName, List<Span> spans) {
    var builder = ExportTraceServiceRequest.newBuilder();
    var resource =
        Resource.newBuilder().addAttributes(OtelShorthand.kv("service.name", serviceName)).build();
    var scopeSpans = ScopeSpans.newBuilder().addAllSpans(spans).build();
    var resourceSpans =
        ResourceSpans.newBuilder().setResource(resource).addScopeSpans(scopeSpans).build();
    builder.addResourceSpans(resourceSpans);
    return builder.build();
  }

  private ExportLogsServiceRequest buildLogsRequest(String serviceName, List<LogRecord> records) {
    var builder = ExportLogsServiceRequest.newBuilder();
    var resource =
        Resource.newBuilder()
            .addAttributes(OtelShorthand.kv("service.name", serviceName))
            .addAttributes(OtelShorthand.kv("service.namespace", "astronomy-shop"))
            .build();
    var scopeLogs = ScopeLogs.newBuilder().addAllLogRecords(records).build();
    var resourceLogs =
        ResourceLogs.newBuilder().setResource(resource).addScopeLogs(scopeLogs).build();
    builder.addResourceLogs(resourceLogs);
    return builder.build();
  }

  private SystemState selectRandomState(List<SystemState> states) {
    var weights = states.stream().map(SystemState::getWeight).toList();
    return RandomUtils.getWeightedRandomSample(states, weights, random);
  }
}

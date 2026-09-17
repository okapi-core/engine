/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.traces.ch;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.gson.Gson;
import com.google.protobuf.InvalidProtocolBufferException;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.trace.v1.ResourceSpans;
import io.opentelemetry.proto.trace.v1.ScopeSpans;
import io.opentelemetry.proto.trace.v1.Span;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.okapi.metrics.ch.ChConstants;
import org.okapi.metrics.ch.ChWriter;
import org.okapi.telemetry.OkapiInternalMetrics;
import org.okapi.traces.core.TracesEventEmitter;

@Slf4j
public class ChTracesWalConsumer {
  private final TracesEventEmitter eventEmitter;
  private final int batchSize;
  private final ChWriter chWriter;
  private final OtelTracesToChRowsConverter converter;
  private final Gson gson = new Gson();
  private final TraceFilterStrategy traceFilterStrategy;
  private final SpanFilterStrategy spanFilterStrategy;
  private final OkapiInternalMetrics metrics;

  public ChTracesWalConsumer(
      TracesEventEmitter eventEmitter,
      int batchSize,
      ChWriter chWriter,
      OtelTracesToChRowsConverter converter,
      TraceFilterStrategy traceFilterStrategy,
      SpanFilterStrategy spanFilterStrategy) {
    this(
        eventEmitter,
        batchSize,
        chWriter,
        converter,
        traceFilterStrategy,
        spanFilterStrategy,
        null);
  }

  public ChTracesWalConsumer(
      TracesEventEmitter eventEmitter,
      int batchSize,
      ChWriter chWriter,
      OtelTracesToChRowsConverter converter,
      TraceFilterStrategy traceFilterStrategy,
      SpanFilterStrategy spanFilterStrategy,
      OkapiInternalMetrics metrics) {
    this.eventEmitter = eventEmitter;
    this.batchSize = batchSize;
    this.chWriter = chWriter;
    this.converter = converter;
    this.traceFilterStrategy = traceFilterStrategy;
    this.spanFilterStrategy = spanFilterStrategy;
    this.metrics = metrics;
  }

  public void consumeRecords() throws IOException, InterruptedException, ExecutionException {
    var batch = eventEmitter.next(batchSize);
    if (metrics != null) {
      metrics.recordConsumerBatch("traces", batch.size());
    }
    List<ChSpansTableRow> rows = new ArrayList<>();
    List<ChSpansIngestedAttribsRow> attribRows = new ArrayList<>();
    List<ChServiceRedEvents> redEvents = new ArrayList<>();

    for (var event : batch) {
      ExportTraceServiceRequest req;
      try {
        req = ExportTraceServiceRequest.parseFrom(event.payload());
      } catch (InvalidProtocolBufferException e) {
        log.warn("Skipping malformed traces event", e);
        continue;
      }
      if (!traceFilterStrategy.shouldPrune(req)) {
        var pruned = pruneSpans(req);
        if (hasSpans(pruned)) {
          rows.addAll(converter.toRows(pruned));
          attribRows.addAll(converter.toAttributeRows(pruned));
        }
      }
      redEvents.addAll(converter.deriveRedEvents(req));
    }

    Multimap<String, String> writeLoad = ArrayListMultimap.create();
    writeLoad.putAll(ChConstants.TBL_SPANS_V1, rows.stream().map(gson::toJson).toList());
    writeLoad.putAll(
        ChConstants.TBL_SPANS_INGESTED_ATTRIBS, attribRows.stream().map(gson::toJson).toList());
    writeLoad.putAll(
        ChConstants.TBL_SERVICE_RED_EVENTS, redEvents.stream().map(gson::toJson).toList());
    chWriter.writeSyncWithBestEffort(writeLoad);

    if (!batch.isEmpty()) {
      eventEmitter.commit();
    }
  }

  protected ExportTraceServiceRequest pruneSpans(ExportTraceServiceRequest request) {
    var builder = ExportTraceServiceRequest.newBuilder();
    for (ResourceSpans resourceSpans : request.getResourceSpansList()) {
      var resourceBuilder = ResourceSpans.newBuilder(resourceSpans);
      resourceBuilder.clearScopeSpans();
      for (ScopeSpans scopeSpans : resourceSpans.getScopeSpansList()) {
        var scopeBuilder = ScopeSpans.newBuilder(scopeSpans);
        scopeBuilder.clearSpans();
        for (Span span : scopeSpans.getSpansList()) {
          if (spanFilterStrategy.shouldPrune(span)) continue;
          scopeBuilder.addSpans(span);
        }
        if (scopeBuilder.getSpansCount() > 0) {
          resourceBuilder.addScopeSpans(scopeBuilder.build());
        }
      }
      if (resourceBuilder.getScopeSpansCount() > 0) {
        builder.addResourceSpans(resourceBuilder.build());
      }
    }
    return builder.build();
  }

  protected boolean hasSpans(ExportTraceServiceRequest request) {
    return request.getResourceSpansList().stream()
        .anyMatch(
            rs -> rs.getScopeSpansList().stream().anyMatch(ss -> !ss.getSpansList().isEmpty()));
  }
}

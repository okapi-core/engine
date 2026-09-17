/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.traces.ch;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.collect.Multimap;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.trace.v1.ResourceSpans;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.okapi.metrics.ch.ChWriter;
import org.okapi.traces.core.FakeTracesEventEmitter;
import org.okapi.traces.core.TracesEvent;

class ChTracesWalConsumerEmitterTests {
  @Test
  void commitsNonEmptyBatchesAfterWritingThem() throws Exception {
    var emitter =
        new FakeTracesEventEmitter(
            List.of(new TracesEvent(ExportTraceServiceRequest.getDefaultInstance().toByteArray())));
    var writer = new RecordingChWriter();
    var consumer =
        new ChTracesWalConsumer(
            emitter,
            10,
            writer,
            new OtelTracesToChRowsConverter(),
            new NoopTraceFilterStrategy(),
            new NoopSpanFilterStrategy());

    consumer.consumeRecords();
    consumer.consumeRecords();

    assertEquals(2, writer.writeCount);
    assertEquals(1, emitter.getCommitCount());
  }

  @Test
  void skipsMalformedRecordsAndCommitsTheBatch() {
    var emitter = new FakeTracesEventEmitter(List.of(new TracesEvent(new byte[] {(byte) 0xff})));
    var writer = new RecordingChWriter();
    var consumer = consumer(emitter, writer);

    assertDoesNotThrow(consumer::consumeRecords);

    assertEquals(1, writer.writeCount);
    assertEquals(1, emitter.getCommitCount());
  }

  @Test
  void continuesProcessingValidRecordsAfterMalformedRecords() {
    var validRequest =
        ExportTraceServiceRequest.newBuilder()
            .addResourceSpans(ResourceSpans.getDefaultInstance())
            .build();
    var emitter =
        new FakeTracesEventEmitter(
            List.of(
                new TracesEvent(new byte[] {(byte) 0xff}),
                new TracesEvent(validRequest.toByteArray())));
    var writer = new RecordingChWriter();
    var converter = new RecordingConverter();
    var consumer =
        new ChTracesWalConsumer(
            emitter,
            10,
            writer,
            converter,
            new NoopTraceFilterStrategy(),
            new NoopSpanFilterStrategy());

    assertDoesNotThrow(consumer::consumeRecords);

    assertEquals(1, converter.deriveRedEventsCount);
    assertEquals(1, writer.writeCount);
    assertEquals(1, emitter.getCommitCount());
  }

  private ChTracesWalConsumer consumer(FakeTracesEventEmitter emitter, RecordingChWriter writer) {
    return new ChTracesWalConsumer(
        emitter,
        10,
        writer,
        new OtelTracesToChRowsConverter(),
        new NoopTraceFilterStrategy(),
        new NoopSpanFilterStrategy());
  }

  private static class RecordingConverter extends OtelTracesToChRowsConverter {
    private int deriveRedEventsCount;

    @Override
    public List<ChServiceRedEvents> deriveRedEvents(ExportTraceServiceRequest request) {
      deriveRedEventsCount++;
      return List.of();
    }
  }

  private static class RecordingChWriter extends ChWriter {
    private int writeCount;

    RecordingChWriter() {
      super(null);
    }

    @Override
    public void writeSyncWithBestEffort(Multimap<String, String> writeLoad) {
      writeCount++;
    }
  }
}

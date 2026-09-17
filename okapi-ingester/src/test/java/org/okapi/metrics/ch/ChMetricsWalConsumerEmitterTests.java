/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.metrics.ch;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.collect.Multimap;
import com.google.gson.Gson;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.okapi.metrics.core.FakeMetricsEventEmitter;
import org.okapi.metrics.core.MetricEvent;
import org.okapi.rest.metrics.ExportMetricsRequest;
import org.okapi.rest.metrics.payloads.Gauge;

class ChMetricsWalConsumerEmitterTests {
  private final Gson gson = new Gson();

  @Test
  void commitsNonEmptyBatchesAfterWritingThem() throws Exception {
    var emitter =
        new FakeMetricsEventEmitter(
            List.of(
                new MetricEvent(
                    gson.toJson(
                            ExportMetricsRequest.builder()
                                .metricName("cpu")
                                .gauge(Gauge.builder().ts(List.of(1L)).value(List.of(2f)).build())
                                .build())
                        .getBytes())));
    var writer = new RecordingChWriter();
    var consumer = new ChMetricsWalConsumer(10, writer, emitter);

    consumer.consumeRecords();
    consumer.consumeRecords();

    assertEquals(2, writer.writeCount);
    assertEquals(1, emitter.getCommitCount());
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

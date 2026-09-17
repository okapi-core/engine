/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.telemetry;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;

public class OkapiInternalMetrics {
  private final MeterRegistry registry;

  public OkapiInternalMetrics(MeterRegistry registry) {
    this.registry = registry;
  }

  public void recordWalAppend(String signal, int payloadCount, long bytes) {
    Counter.builder("okapi.wal.appended")
        .tag("signal", signal)
        .register(registry)
        .increment(payloadCount);
    Counter.builder("okapi.wal.bytes").tag("signal", signal).register(registry).increment(bytes);
  }

  public void recordConsumerBatch(String signal, int eventCount) {
    if (eventCount == 0) {
      return;
    }
    Counter.builder("okapi.consumer.batches").tag("signal", signal).register(registry).increment();
    Counter.builder("okapi.consumer.events")
        .tag("signal", signal)
        .register(registry)
        .increment(eventCount);
  }

  public void recordStorageWrite(String table, int rowCount, long durationNanos) {
    Counter.builder("okapi.storage.write.rows")
        .tag("table", table)
        .register(registry)
        .increment(rowCount);
    Timer.builder("okapi.storage.write.duration")
        .tag("table", table)
        .publishPercentileHistogram()
        .register(registry)
        .record(durationNanos, TimeUnit.NANOSECONDS);
  }
}

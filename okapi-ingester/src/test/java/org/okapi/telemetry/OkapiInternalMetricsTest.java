/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class OkapiInternalMetricsTest {

  @Test
  void recordsDomainCountersAndStorageTimer() {
    var registry = new SimpleMeterRegistry();
    var metrics = new OkapiInternalMetrics(registry);

    metrics.recordWalAppend("logs", 2, 128);
    metrics.recordConsumerBatch("logs", 2);
    metrics.recordConsumerBatch("logs", 0);
    metrics.recordStorageWrite("logs", 3, 1_000_000);

    assertEquals(2.0, registry.get("okapi.wal.appended").tag("signal", "logs").counter().count());
    assertEquals(128.0, registry.get("okapi.wal.bytes").tag("signal", "logs").counter().count());
    assertEquals(
        2.0, registry.get("okapi.consumer.events").tag("signal", "logs").counter().count());
    assertEquals(
        3.0, registry.get("okapi.storage.write.rows").tag("table", "logs").counter().count());
    assertEquals(
        1.0, registry.get("okapi.storage.write.duration").tag("table", "logs").timer().count());
  }
}

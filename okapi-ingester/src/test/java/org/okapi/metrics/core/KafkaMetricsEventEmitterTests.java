/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.metrics.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;

class KafkaMetricsEventEmitterTests {
  @Test
  void buffersPolledRecordsAndCommitsOnlyReturnedOffsets() {
    var topic = "metrics";
    var partition = new TopicPartition(topic, 0);
    var consumer = new MockConsumer<byte[], byte[]>(OffsetResetStrategy.EARLIEST);
    consumer.schedulePollTask(
        () -> {
          consumer.rebalance(List.of(partition));
          consumer.updateBeginningOffsets(java.util.Map.of(partition, 0L));
          consumer.addRecord(new ConsumerRecord<>(topic, 0, 0, null, new byte[] {1}));
          consumer.addRecord(new ConsumerRecord<>(topic, 0, 1, null, new byte[] {2}));
        });
    var emitter = new KafkaMetricsEventEmitter(consumer, topic, Duration.ZERO);

    var first = emitter.next(1);
    emitter.commit();
    var second = emitter.next(1);
    emitter.commit();

    assertArrayEquals(new byte[] {1}, first.getFirst().payload());
    assertArrayEquals(new byte[] {2}, second.getFirst().payload());
    assertEquals(2L, consumer.committed(java.util.Set.of(partition)).get(partition).offset());
  }
}

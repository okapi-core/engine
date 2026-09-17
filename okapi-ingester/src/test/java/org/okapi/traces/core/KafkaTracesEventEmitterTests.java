/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.traces.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;

class KafkaTracesEventEmitterTests {
  @Test
  void ignoresBatchSizeAndCommitsAllPolledRecords() {
    var topic = "traces";
    var partition = new TopicPartition(topic, 0);
    var consumer = new MockConsumer<byte[], byte[]>(OffsetResetStrategy.EARLIEST);
    consumer.schedulePollTask(
        () -> {
          consumer.rebalance(List.of(partition));
          consumer.updateBeginningOffsets(java.util.Map.of(partition, 0L));
          consumer.addRecord(new ConsumerRecord<>(topic, 0, 0, null, new byte[] {1}));
          consumer.addRecord(new ConsumerRecord<>(topic, 0, 1, null, new byte[] {2}));
        });
    var emitter = new KafkaTracesEventEmitter(consumer, topic, Duration.ZERO);

    var batch = emitter.next(1);
    emitter.commit();

    assertEquals(2, batch.size());
    assertArrayEquals(new byte[] {1}, batch.get(0).payload());
    assertArrayEquals(new byte[] {2}, batch.get(1).payload());
    assertEquals(2L, consumer.committed(java.util.Set.of(partition)).get(partition).offset());
  }
}

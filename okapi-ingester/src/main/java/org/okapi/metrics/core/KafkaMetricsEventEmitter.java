/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.metrics.core;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;

public class KafkaMetricsEventEmitter implements MetricsEventEmitter, AutoCloseable {
  private final Consumer<byte[], byte[]> consumer;
  private final Duration pollTimeout;
  private final Map<TopicPartition, OffsetAndMetadata> pendingCommit = new HashMap<>();

  public KafkaMetricsEventEmitter(
      Consumer<byte[], byte[]> consumer, String topic, Duration pollTimeout) {
    this.consumer = consumer;
    this.pollTimeout = pollTimeout;
    consumer.subscribe(List.of(topic));
  }

  @Override
  public List<MetricEvent> next(int ignoredBatchSize) {
    if (!pendingCommit.isEmpty()) {
      throw new IllegalStateException("The previous metrics event batch must be committed first");
    }

    var batch = new ArrayList<MetricEvent>();
    consumer
        .poll(pollTimeout)
        .forEach(
            record -> {
              batch.add(new MetricEvent(record.value()));
              pendingCommit.merge(
                  new TopicPartition(record.topic(), record.partition()),
                  new OffsetAndMetadata(record.offset() + 1),
                  (left, right) -> left.offset() >= right.offset() ? left : right);
            });
    return batch;
  }

  @Override
  public void commit() {
    if (!pendingCommit.isEmpty()) {
      consumer.commitSync(pendingCommit);
      pendingCommit.clear();
    }
  }

  @Override
  public void close() {
    consumer.close();
  }
}

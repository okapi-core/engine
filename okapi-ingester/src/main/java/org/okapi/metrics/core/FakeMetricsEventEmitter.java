/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.metrics.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Queue;
import lombok.Getter;

public class FakeMetricsEventEmitter implements MetricsEventEmitter {
  private final Queue<MetricEvent> events = new ArrayDeque<>();
  @Getter private int commitCount;

  public FakeMetricsEventEmitter(Collection<MetricEvent> events) {
    this.events.addAll(events);
  }

  @Override
  public List<MetricEvent> next(int batchSize) {
    var batch = new ArrayList<MetricEvent>();
    while (batch.size() < batchSize && !events.isEmpty()) {
      batch.add(events.remove());
    }
    return batch;
  }

  @Override
  public void commit() {
    commitCount++;
  }
}

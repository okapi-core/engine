/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.traces.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Queue;
import lombok.Getter;

public class FakeTracesEventEmitter implements TracesEventEmitter {
  private final Queue<TracesEvent> events = new ArrayDeque<>();
  @Getter private int commitCount;

  public FakeTracesEventEmitter(Collection<TracesEvent> events) {
    this.events.addAll(events);
  }

  public void add(TracesEvent event) {
    events.add(event);
  }

  @Override
  public List<TracesEvent> next(int batchSize) {
    var batch = new ArrayList<TracesEvent>();
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

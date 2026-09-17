/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.logs.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Queue;
import lombok.Getter;

public class FakeLogsEventEmitter implements LogsEventEmitter {
  private final Queue<LogsEvent> events = new ArrayDeque<>();
  @Getter private int commitCount;

  public FakeLogsEventEmitter(Collection<LogsEvent> events) {
    this.events.addAll(events);
  }

  @Override
  public List<LogsEvent> next(int batchSize) {
    var batch = new ArrayList<LogsEvent>();
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

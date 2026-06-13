/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.logs.core;

import java.io.IOException;
import java.util.List;
import org.okapi.metrics.ch.ChWalResources;
import org.okapi.wal.frame.WalEntry;
import org.okapi.wal.lsn.Lsn;

public class WalLogsEventEmitter implements LogsEventEmitter {
  private final ChWalResources resources;
  private Lsn pendingCommit;

  public WalLogsEventEmitter(ChWalResources resources) {
    this.resources = resources;
  }

  @Override
  public List<LogsEvent> next(int batchSize) throws IOException {
    var batch = resources.getReader().readBatchAndAdvance(batchSize);
    pendingCommit = batch.isEmpty() ? null : WalEntry.getMaxLsn(batch);
    return batch.stream().map(entry -> new LogsEvent(entry.getPayload())).toList();
  }

  @Override
  public void commit() throws IOException {
    if (pendingCommit != null) {
      resources.getManager().commitLsn(pendingCommit);
      pendingCommit = null;
    }
  }
}

/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.metrics.ch;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import org.okapi.telemetry.OkapiInternalMetrics;
import org.okapi.wal.LsnSupplier;
import org.okapi.wal.factory.WalResourceBundle;
import org.okapi.wal.factory.WalResourcesFactory;
import org.okapi.wal.frame.WalEntry;
import org.okapi.wal.io.IllegalWalEntryException;
import org.okapi.wal.io.WalReader;
import org.okapi.wal.io.WalWriter;
import org.okapi.wal.manager.WalManager;

public class ChWalResources {
  Path walDir;
  WalResourceBundle resourceBundle;
  LsnSupplier lsnSupplier;
  private final String signal;
  private final OkapiInternalMetrics metrics;

  public ChWalResources(Path walDir, WalManager.WalConfig config) throws IOException {
    this(walDir, config, "unknown", new OkapiInternalMetrics(new SimpleMeterRegistry()));
  }

  public ChWalResources(
      Path walDir, WalManager.WalConfig config, String signal, OkapiInternalMetrics metrics)
      throws IOException {
    this.walDir = walDir;
    this.signal = signal;
    this.metrics = metrics;
    var resourceFactory = new WalResourcesFactory(config);
    this.resourceBundle = resourceFactory.createResourcesFromScratch(this.walDir);
    this.lsnSupplier = resourceBundle.getLsnSupplier();
  }

  public WalWriter getWriter() {
    return this.resourceBundle.getWriter();
  }

  public WalReader getReader() {
    return this.resourceBundle.getReader();
  }

  public WalManager getManager() {
    return this.resourceBundle.getManager();
  }

  public LsnSupplier getSupplier() throws IOException {
    return this.resourceBundle.getLsnSupplier();
  }

  public synchronized void appendPayloads(Collection<byte[]> payloads)
      throws IllegalWalEntryException, IOException {
    if (payloads.isEmpty()) {
      return;
    }
    List<WalEntry> walEntries =
        payloads.stream().map(payload -> new WalEntry(lsnSupplier.next(), payload)).toList();
    resourceBundle.getWriter().appendBatch(walEntries);
    metrics.recordWalAppend(
        signal, payloads.size(), payloads.stream().mapToLong(p -> p.length).sum());
  }
}

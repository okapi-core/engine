/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.wal.factory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.okapi.wal.frame.WalEntry;
import org.okapi.wal.io.IllegalWalEntryException;
import org.okapi.wal.io.WalWriter;
import org.okapi.wal.lsn.Lsn;
import org.okapi.wal.manager.WalDirectory;
import org.okapi.wal.manager.WalManager;

public class WalResourcesFactoryMultisegTests {
  @TempDir Path dir;

  WalManager.WalConfig smallConfig = new WalManager.WalConfig(40L);

  WalEntry e1 = new WalEntry(Lsn.fromNumber(10), "a".getBytes());
  WalEntry e2 = new WalEntry(Lsn.fromNumber(20), "b".getBytes());
  WalEntry e3 = new WalEntry(Lsn.fromNumber(30), "c".getBytes());
  WalEntry e4 = new WalEntry(Lsn.fromNumber(40), "d".getBytes());

  @BeforeEach
  void setup() throws IOException {
    var walDir = new WalDirectory(dir);
    try (var walManager = new WalManager(walDir, smallConfig)) {
      var writer = new WalWriter(walManager, walDir);
      writer.appendBatch(List.of(e1, e2, e3, e4));
    } catch (IllegalWalEntryException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void readAcrossSegments() throws IOException {
    var factory = new WalResourcesFactory(smallConfig);
    var bundle = factory.createResourcesFromScratch(dir);
    var reader = bundle.getReader();
    var batch = reader.readBatchAndAdvance(5);
    var lsns = batch.stream().map(e -> e.getLsn().getNumber()).toList();
    var payloads = batch.stream().map(e -> new String(e.getPayload())).toList();
    assertEquals(List.of(10L, 20L, 30L, 40L), lsns);
    assertEquals(List.of("a", "b", "c", "d"), payloads);
    assertEquals(41L, bundle.getLsnSupplier().next().getNumber());
  }

  @Test
  void startFromMidSegment() throws IOException {
    var factory = new WalResourcesFactory(smallConfig);
    var bundle = factory.createResourcesWithFlushedLsn(dir, Lsn.fromNumber(20));
    var reader = bundle.getReader();
    var batch = reader.readBatchAndAdvance(5);
    var lsns = batch.stream().map(e -> e.getLsn().getNumber()).toList();
    assertEquals(List.of(30L, 40L), lsns);
    assertEquals(41L, bundle.getLsnSupplier().next().getNumber());
  }
}

/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.wal.factory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.okapi.wal.frame.WalEntry;
import org.okapi.wal.io.IllegalWalEntryException;
import org.okapi.wal.io.WalWriter;
import org.okapi.wal.lsn.Lsn;
import org.okapi.wal.manager.WalDirectory;
import org.okapi.wal.manager.WalManager;

public class WalResourcesFactoryTests {
  @TempDir Path dir;

  WalManager.WalConfig normalConfig = new WalManager.WalConfig(100L);
  WalEntry cmd1 = new WalEntry(Lsn.fromNumber(10), "1".getBytes());
  WalEntry cmd2 = new WalEntry(Lsn.fromNumber(20), "2".getBytes());
  WalEntry cmd3 = new WalEntry(Lsn.fromNumber(30), "3".getBytes());

  @BeforeEach
  void setup() throws IOException {
    var walDir = new WalDirectory(dir);
    try (var walManager = new WalManager(walDir, normalConfig)) {
      var writer = new WalWriter(walManager, walDir);
      writer.appendBatch(List.of(cmd1, cmd2, cmd3));
    } catch (IllegalWalEntryException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void walCommitNonExistent() throws IOException {
    var resourceFactory = new WalResourcesFactory(normalConfig);
    var bundle = resourceFactory.createResourcesFromScratch(dir);
    var reader = bundle.getReader();
    var batch = reader.readBatchAndAdvance(3);
    var lsns = batch.stream().map(e -> e.getLsn().getNumber()).toList();
    var cmds = batch.stream().map(e -> new String(e.getPayload())).toList();
    Assertions.assertEquals(List.of(10L, 20L, 30L), lsns);
    Assertions.assertEquals(List.of("1", "2", "3"), cmds);
    Assertions.assertEquals(31L, bundle.getLsnSupplier().getLsn().getNumber());
  }

  @Test
  void walCommitIsBeforeExpected() {
    var resourceFactory = new WalResourcesFactory(normalConfig);
    try (var walManager = new WalManager(dir, normalConfig)) {
      walManager.commitLsn(Lsn.fromNumber(10));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    try {
      var bundle = resourceFactory.createResourcesWithFlushedLsn(dir, Lsn.fromNumber(20));
      var reader = bundle.getReader();
      var batch = reader.readBatchAndAdvance(3);
      var lsns = batch.stream().map(e -> e.getLsn().getNumber()).toList();
      Assertions.assertEquals(List.of(30L), lsns);
      Assertions.assertEquals(31L, bundle.getLsnSupplier().getLsn().getNumber());
      var walDir = new WalDirectory(dir);
      var latestCommit = walDir.getLatestCommit().orElseThrow();
      Assertions.assertEquals(20L, latestCommit.getLsn().getNumber());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void walCommitIsAsExpected() {
    var resourceFactory = new WalResourcesFactory(normalConfig);
    try (var walManager = new WalManager(dir, normalConfig)) {
      walManager.commitLsn(Lsn.fromNumber(20));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    try {
      var bundle = resourceFactory.createResourcesWithFlushedLsn(dir, Lsn.fromNumber(20));
      var reader = bundle.getReader();
      var batch = reader.readBatchAndAdvance(3);
      var lsns = batch.stream().map(e -> e.getLsn().getNumber()).toList();
      Assertions.assertEquals(List.of(30L), lsns);
      Assertions.assertEquals(31L, bundle.getLsnSupplier().getLsn().getNumber());
      var walDir = new WalDirectory(dir);
      var latestCommit = walDir.getLatestCommit().orElseThrow();
      Assertions.assertEquals(20L, latestCommit.getLsn().getNumber());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void walCommitAfterExpected() {
    var resourceFactory = new WalResourcesFactory(normalConfig);
    try (var walManager = new WalManager(dir, normalConfig)) {
      walManager.commitLsn(Lsn.fromNumber(30));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    try {
      var bundle = resourceFactory.createResourcesWithFlushedLsn(dir, Lsn.fromNumber(20));
      var reader = bundle.getReader();
      var batch = reader.readBatchAndAdvance(3);
      Assertions.assertTrue(batch.isEmpty());
      Assertions.assertEquals(31L, bundle.getLsnSupplier().getLsn().getNumber());
      var walDir = new WalDirectory(dir);
      var latestCommit = walDir.getLatestCommit().orElseThrow();
      Assertions.assertEquals(30L, latestCommit.getLsn().getNumber());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void emptyDirectoryCreatesAndSuppliesStartLsn() throws IOException {
    var emptyDir = dir.resolve("empty");
    var resourceFactory = new WalResourcesFactory(normalConfig);
    var bundle = resourceFactory.createResourcesFromScratch(emptyDir);
    Assertions.assertEquals(1L, bundle.getLsnSupplier().next().getNumber());
    var reader = bundle.getReader();
    Assertions.assertTrue(reader.readNext().isEmpty());
  }

  @Test
  void writerAppendAfterRecoveryVisibleToReader() throws IOException, IllegalWalEntryException {
    var resourceFactory = new WalResourcesFactory(normalConfig);
    var bundle = resourceFactory.createResourcesFromScratch(dir);
    var supplier = bundle.getLsnSupplier();
    var writer = bundle.getWriter();
    var newEntry = new WalEntry(supplier.next(), "new".getBytes());
    writer.append(newEntry);

    var reader = bundle.getReader();
    var batch = reader.readBatchAndAdvance(4);
    var payloads = batch.stream().map(e -> new String(e.getPayload())).toList();
    Assertions.assertEquals(List.of("1", "2", "3", "new"), payloads);
    Assertions.assertEquals(32L, bundle.getLsnSupplier().next().getNumber());
  }
}

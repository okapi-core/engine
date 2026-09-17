/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.wal.io;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.okapi.wal.frame.WalEntry;
import org.okapi.wal.lsn.Lsn;
import org.okapi.wal.manager.WalDirectory;
import org.okapi.wal.manager.WalManager;

public class WalWriterTests {
  @TempDir Path temp;

  @Test
  void testWriteSingleRecord() throws IOException, IllegalWalEntryException {
    var walManager = new WalManager(temp, sampleConfig());
    var walDir = new WalDirectory(temp);
    var walWriter = new WalWriter(walManager, walDir);
    var entry = new WalEntry(Lsn.fromNumber(1), sampleCmd().getBytes());
    walWriter.append(entry);
    Assertions.assertEquals(1L, walWriter.getLastWrittenLsn().getNumber());
    Assertions.assertEquals(1, walManager.getMaxLsnInSegment(0).get());
  }

  @Test
  void testSegmentRollover() throws IOException, IllegalWalEntryException {
    var walManager = new WalManager(temp, sampleConfig());
    var walDir = new WalDirectory(temp);
    var walWriter = new WalWriter(walManager, walDir);
    var entry = new WalEntry(Lsn.fromNumber(1), sampleCmd().getBytes());
    walWriter.append(entry);
    var largeEntry = new WalEntry(Lsn.fromNumber(2), largeCmd());
    walWriter.append(largeEntry);
    var entryAfterRollOver = new WalEntry(Lsn.fromNumber(3), sampleCmd().getBytes());
    walWriter.append(entryAfterRollOver);
    Assertions.assertEquals(3L, walWriter.getLastWrittenLsn().getNumber());
    Assertions.assertEquals(2, walManager.getMaxLsnInSegment(0).get());
    Assertions.assertEquals(3, walManager.getMaxLsnInSegment(1).get());
  }

  @Test
  void testWriteFaulty() throws IOException, IllegalWalEntryException {
    var walManager = new WalManager(temp, sampleConfig());
    var walDir = new WalDirectory(temp);
    var walWriter = new WalWriter(walManager, walDir);
    var entry = new WalEntry(Lsn.fromNumber(1), sampleCmd().getBytes());
    walWriter.append(entry);
    Assertions.assertThrows(IllegalWalEntryException.class, () -> walWriter.append(entry));
  }

  @Test
  void testAppendBatch() throws IOException, IllegalWalEntryException {
    var lsn = new AtomicLong(0L);
    var batch =
        List.of(sampleCmd(), sampleCmd(), sampleCmd(), sampleCmd()).stream()
            .map(
                cmd -> {
                  return new WalEntry(new Lsn(lsn.incrementAndGet()), cmd.getBytes());
                })
            .toList();
    var walManager = new WalManager(temp, sampleConfig());
    var walDir = new WalDirectory(temp);
    var walWriter = new WalWriter(walManager, walDir);
    walWriter.appendBatch(batch);
    Assertions.assertEquals(4, walManager.getMaxLsnInSegment(0).get());
  }

  @Test
  void testAppendBatchRotationPersistsMetadata() throws IOException, IllegalWalEntryException {
    var walManager = new WalManager(temp, smallConfig());
    var walDir = new WalDirectory(temp);
    var walWriter = new WalWriter(walManager, walDir);
    var batch =
        List.of(
            new WalEntry(Lsn.fromNumber(1), "A".getBytes()),
            new WalEntry(Lsn.fromNumber(2), "B".getBytes()),
            new WalEntry(Lsn.fromNumber(3), "C".getBytes()));

    walWriter.appendBatch(batch);

    var metadata = walDir.getSegmentMetadata().orElseThrow();
    Assertions.assertEquals(1, metadata.getSegmentMetadata().size());
    var meta = metadata.getSegmentMetadata().get(0);
    Assertions.assertEquals(0, meta.getSegmentNumber());
    Assertions.assertEquals(2, meta.getLargestLsn());
    Assertions.assertEquals(3L, walWriter.getLastWrittenLsn().getNumber());
    Assertions.assertEquals(3, walManager.getMaxLsnInSegment(1).get());
  }

  public static WalManager.WalConfig sampleConfig() {
    return new WalManager.WalConfig(100L);
  }

  public static WalManager.WalConfig smallConfig() {
    return new WalManager.WalConfig(50L);
  }

  public static String sampleCmd() {
    return "WRITE";
  }

  public static byte[] largeCmd() {
    return new byte[100];
  }
}

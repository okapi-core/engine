/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.wal.manager;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.okapi.wal.exceptions.CorruptedRecordException;
import org.okapi.wal.frame.WalEntry;
import org.okapi.wal.io.IllegalWalEntryException;
import org.okapi.wal.io.SegmentReader;
import org.okapi.wal.io.WalWriter;
import org.okapi.wal.lsn.Lsn;
import org.okapi.wal.util.WalTestWriteUtils;

public class SegmentTruncatorTests {

  @TempDir Path dir;

  @Test
  void truncatorKeepsConsistentEntries()
      throws IOException, CorruptedRecordException, IllegalWalEntryException {
    var segment = dir.resolve("segment_0_log");
    var entry1 = new WalEntry(Lsn.fromNumber(10), "one".getBytes());
    var entry2 = new WalEntry(Lsn.fromNumber(20), "two".getBytes());
    try (var walManager = new WalManager(dir, new WalManager.WalConfig(10_000L));
        var walWriter = new WalWriter(walManager, new WalDirectory(dir))) {
      walWriter.append(entry1);
      walWriter.append(entry2);
    }

    var result = SegmentTruncator.truncate(segment);

    assertTrue(result.getLastGoodLsn().isPresent());
    assertEquals(entry2.getLsn(), result.getLastGoodLsn().get());
    assertEquals(segment.toFile().length(), result.getTruncatedToBytes());

    try (var reader = new SegmentReader(0, segment)) {
      var r1 = reader.readNextRecord();
      var r2 = reader.readNextRecord();
      var r3 = reader.readNextRecord();
      assertTrue(r1.isPresent());
      assertTrue(r2.isPresent());
      assertFalse(r3.isPresent());
      assertEquals(entry1, r1.get());
      assertEquals(entry2, r2.get());
    }
  }

  @Test
  void truncatorRemovesTornTail()
      throws IOException, CorruptedRecordException, IllegalWalEntryException {
    var segment = dir.resolve("segment_0_log");
    var entry1 = new WalEntry(Lsn.fromNumber(10), "one".getBytes());
    var entry2 = new WalEntry(Lsn.fromNumber(20), "two".getBytes());
    try (var walManager = new WalManager(dir, new WalManager.WalConfig(10_000L));
        var walWriter = new WalWriter(walManager, new WalDirectory(dir))) {
      walWriter.append(entry1);
    }
    WalTestWriteUtils.writePartialEntry(segment, entry2);

    var result = SegmentTruncator.truncate(segment);

    assertTrue(result.getLastGoodLsn().isPresent());
    assertEquals(entry1.getLsn(), result.getLastGoodLsn().get());

    try (var reader = new SegmentReader(0, segment)) {
      var r1 = reader.readNextRecord();
      var r2 = reader.readNextRecord();
      assertTrue(r1.isPresent());
      assertFalse(r2.isPresent());
      assertEquals(entry1, r1.get());
    }
  }
}

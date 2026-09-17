/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.wal.manager;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.okapi.wal.frame.WalEntry;
import org.okapi.wal.io.IllegalWalEntryException;
import org.okapi.wal.io.SegmentReader;
import org.okapi.wal.io.WalReader;
import org.okapi.wal.io.WalWriter;
import org.okapi.wal.lsn.Lsn;
import org.okapi.wal.util.WalTestWriteUtils;

public class WalManagerTornDirTests {

  @TempDir Path dir;

  Path seg0;
  Path seg1;
  WalEntry e10 = new WalEntry(Lsn.fromNumber(10), "one".getBytes());
  WalEntry e20 = new WalEntry(Lsn.fromNumber(20), "two".getBytes());
  WalEntry e30 = new WalEntry(Lsn.fromNumber(30), "three".getBytes());
  WalEntry e40 = new WalEntry(Lsn.fromNumber(40), "four".getBytes());
  WalEntry e50 = new WalEntry(Lsn.fromNumber(50), "five".getBytes());
  WalManager.WalConfig config = new WalManager.WalConfig(40L);

  @BeforeEach
  void setupDir() throws IOException, IllegalWalEntryException {
    seg0 = dir.resolve("segment_0_log");
    seg1 = dir.resolve("segment_1_log");
    try (var walManager = new WalManager(dir, config);
        var walWriter = new WalWriter(walManager, new WalDirectory(dir))) {
      walWriter.append(e10);
      walWriter.append(e20);
      walWriter.append(e30);
      walWriter.append(e40);
    }
    WalTestWriteUtils.writePartialEntry(seg1, e50); // torn tail

    var metadata = WalSegmentsMetadata.ofEmpty();
    metadata.addMetadata(
        WalSegmentMetadata.builder().segmentNumber(0).largestLsn(e20.getLsn().getNumber()).build());
    metadata.addMetadata(
        WalSegmentMetadata.builder().segmentNumber(1).largestLsn(e40.getLsn().getNumber()).build());
    var gson = new Gson();
    Files.writeString(dir.resolve("segment_table.json"), gson.toJson(metadata));
  }

  @Test
  void repairsTornTailAndReadsSegments() throws Exception {
    try (var walManager = new WalManager(dir, config)) {
      assertEquals(e40.getLsn(), walManager.getLastWrittenLsn());

      try (var reader0 = new SegmentReader(0, seg0);
          var reader1 = new SegmentReader(1, seg1)) {
        assertEquals(e10, reader0.readNextRecord().orElseThrow());
        assertEquals(e20, reader0.readNextRecord().orElseThrow());
        assertTrue(reader0.readNextRecord().isEmpty());

        assertEquals(e30, reader1.readNextRecord().orElseThrow());
        assertEquals(e40, reader1.readNextRecord().orElseThrow());
        assertTrue(reader1.readNextRecord().isEmpty());
      }
    }
  }

  @Test
  void readsSequenceFromStartAndMiddle() throws Exception {
    try (var walManager = new WalManager(dir, config);
        var writer = new WalWriter(walManager, new WalDirectory(dir))) {
      var readerFromStart =
          new WalReader(
              walManager, new WalDirectory(dir), Lsn.fromNumber(0), writer::getLastWrittenLsn);
      assertLsns(readerFromStart, List.of(10L, 20L, 30L, 40L));

      var readerFromMid =
          new WalReader(
              walManager, new WalDirectory(dir), Lsn.fromNumber(15), writer::getLastWrittenLsn);
      assertLsns(readerFromMid, List.of(20L, 30L, 40L));
    }
  }

  @Test
  void readerSeesNewWritesAfterTail() throws Exception {
    try (var walManager = new WalManager(dir, config);
        var writer = new WalWriter(walManager, new WalDirectory(dir))) {
      writer.append(e50); // advances lastWritten to 50
      var readerAtEnd =
          new WalReader(
              walManager,
              new WalDirectory(dir),
              writer.getLastWrittenLsn(),
              writer::getLastWrittenLsn);
      assertTrue(readerAtEnd.readNext().isEmpty());

      var e60 = new WalEntry(Lsn.fromNumber(60), "six".getBytes());
      writer.append(e60);
      var next = readerAtEnd.readNext();
      assertTrue(next.isPresent());
      assertEquals(e60, next.get());
    }
  }

  @Test
  void rejectsOutOfOrderWrite() throws Exception {
    try (var walManager = new WalManager(dir, config);
        var writer = new WalWriter(walManager, new WalDirectory(dir))) {
      var low = new WalEntry(Lsn.fromNumber(15), "low".getBytes());
      assertThrows(IllegalWalEntryException.class, () -> writer.append(low));
    }
  }

  private void assertLsns(WalReader reader, List<Long> expected) throws IOException {
    var batch = reader.readBatchAndAdvance(expected.size() + 1);
    var lsns = batch.stream().map(e -> e.getLsn().getNumber()).toList();
    assertEquals(expected, lsns);
  }
}

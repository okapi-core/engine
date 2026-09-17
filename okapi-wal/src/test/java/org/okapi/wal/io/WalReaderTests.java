/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.wal.io;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.okapi.wal.frame.WalEntry;
import org.okapi.wal.lsn.Lsn;
import org.okapi.wal.manager.WalDirectory;
import org.okapi.wal.manager.WalManager;

public class WalReaderTests {
  @TempDir Path temp;

  String largePayload1 = "abcdefghij";
  String largePayload2 = "klmnopqrst";

  @Test
  void testSingleRecordRead() throws IOException, IllegalWalEntryException {
    var walManager = new WalManager(temp, sampleConfig());
    var walDir = new WalDirectory(temp);
    var walWriter = new WalWriter(walManager, walDir);
    var reader = walWriter.getReaderFromCurrent();
    Assertions.assertTrue(reader.readBatchAndAdvance(1).isEmpty());

    var entry1 = makeWalEntry(1, "ONE");
    walWriter.append(entry1);
    var batch = reader.readBatchAndAdvance(1);
    Assertions.assertEquals(List.of(1L), collectLsn(batch));
    Assertions.assertEquals(List.of("ONE"), collectPayloads(batch));
  }

  @Test
  void testReadFromTwoDifferentPoints() throws IOException, IllegalWalEntryException {
    // test should write two records and create a reader after writing each. First reader should
    // supply only the later record (entries after reader creation), second reader should supply
    // none, and a 3rd reader should also supply none. A reader created before any writes should
    // see both records.
    var walManager = new WalManager(temp, sampleConfig());
    var walDir = new WalDirectory(temp);
    var walWriter = new WalWriter(walManager, walDir);

    var reader0 = walWriter.getReaderFromCurrent();

    var entry1 = makeWalEntry(1, "ONE");
    walWriter.append(entry1);
    var reader1 = walWriter.getReaderFromCurrent();

    var entry2 = makeWalEntry(2, "TWO");
    walWriter.append(entry2);
    var reader2 = walWriter.getReaderFromCurrent();

    var reader0Batch = reader0.readBatchAndAdvance(2);
    Assertions.assertEquals(List.of(1L, 2L), collectLsn(reader0Batch));
    Assertions.assertEquals(List.of("ONE", "TWO"), collectPayloads(reader0Batch));
    Assertions.assertTrue(reader0.readBatchAndAdvance(1).isEmpty());

    var reader1Batch = reader1.readBatchAndAdvance(2);
    Assertions.assertEquals(List.of(2L), collectLsn(reader1Batch));
    Assertions.assertEquals(List.of("TWO"), collectPayloads(reader1Batch));
    Assertions.assertTrue(reader1.readBatchAndAdvance(1).isEmpty());

    Assertions.assertTrue(reader2.readBatchAndAdvance(1).isEmpty());
  }

  @Test
  void testReadMultipleRecords() throws IOException, IllegalWalEntryException {
    // create a writer, then create a reader. Read from the reader and assert no entry is present.
    // Then write a record to the writer. Then read a record and assert its present and the contents
    // are correct.
    // then write another record. then do another read and assert that a entry is obtained and its
    // contents are correct.
    var walManager = new WalManager(temp, sampleConfig());
    var walDir = new WalDirectory(temp);
    var walWriter = new WalWriter(walManager, walDir);
    var reader = walWriter.getReaderFromCurrent();

    Assertions.assertTrue(reader.readBatchAndAdvance(1).isEmpty());

    var entry1 = makeWalEntry(1, "ONE");
    walWriter.append(entry1);
    var read1 = reader.readBatchAndAdvance(1);
    Assertions.assertEquals(List.of(1L), collectLsn(read1));
    Assertions.assertEquals(List.of("ONE"), collectPayloads(read1));

    var entry2 = makeWalEntry(2, "TWO");
    walWriter.append(entry2);
    var read2 = reader.readBatchAndAdvance(1);
    Assertions.assertEquals(List.of(2L), collectLsn(read2));
    Assertions.assertEquals(List.of("TWO"), collectPayloads(read2));
  }

  @Test
  void testReadLargePayloads_readSingleRecord() throws IOException, IllegalWalEntryException {
    // reader created before writes should read back a single large payload record
    var walManager = new WalManager(temp, smallConfig());
    var walDir = new WalDirectory(temp);
    var walWriter = new WalWriter(walManager, walDir);
    var reader = walWriter.getReaderFromCurrent();

    Assertions.assertTrue(reader.readBatchAndAdvance(1).isEmpty());

    walWriter.append(makeWalEntry(1, largePayload1));
    var batch = reader.readBatchAndAdvance(1);
    Assertions.assertEquals(List.of(1L), collectLsn(batch));
    Assertions.assertEquals(List.of(largePayload1), collectPayloads(batch));
    Assertions.assertTrue(reader.readBatchAndAdvance(1).isEmpty());
  }

  @Test
  void testReadLargePayloads_readMultipleRecords() throws IOException, IllegalWalEntryException {
    // reader created before writes should read back multiple large payload records in order
    var walManager = new WalManager(temp, smallConfig());
    var walDir = new WalDirectory(temp);
    var walWriter = new WalWriter(walManager, walDir);
    var reader = walWriter.getReaderFromCurrent();

    walWriter.append(makeWalEntry(1, largePayload1));
    walWriter.append(makeWalEntry(2, largePayload2));

    var batch = reader.readBatchAndAdvance(2);
    Assertions.assertEquals(List.of(1L, 2L), collectLsn(batch));
    Assertions.assertEquals(List.of(largePayload1, largePayload2), collectPayloads(batch));
    Assertions.assertTrue(reader.readBatchAndAdvance(1).isEmpty());
  }

  @Test
  void testReadLargePayloads_noRecords() throws IOException {
    // reader created with no writes should return empty batch
    var walManager = new WalManager(temp, smallConfig());
    var walDir = new WalDirectory(temp);
    var walWriter = new WalWriter(walManager, walDir);
    var reader = walWriter.getReaderFromCurrent();

    Assertions.assertTrue(reader.readBatchAndAdvance(3).isEmpty());
  }

  @Test
  void testReaderAdvancesAcrossRotation() throws IOException, IllegalWalEntryException {
    var walManager = new WalManager(temp, rotationConfig());
    var walDir = new WalDirectory(temp);
    var walWriter = new WalWriter(walManager, walDir);
    var reader = walWriter.getReaderFromCurrent();

    walWriter.append(makeWalEntry(1, "A"));
    walWriter.append(makeWalEntry(2, "B"));
    walWriter.append(makeWalEntry(3, "C"));

    var batch = reader.readBatchAndAdvance(3);
    Assertions.assertEquals(List.of(1L, 2L, 3L), collectLsn(batch));
    Assertions.assertEquals(List.of("A", "B", "C"), collectPayloads(batch));
    Assertions.assertTrue(reader.readBatchAndAdvance(1).isEmpty());
  }

  @Test
  void testReaderSkipsOldSegmentsWhenStartingLate() throws IOException, IllegalWalEntryException {
    var walManager = new WalManager(temp, rotationConfig());
    var walDir = new WalDirectory(temp);
    var walWriter = new WalWriter(walManager, walDir);

    walWriter.append(makeWalEntry(1, "A"));
    walWriter.append(makeWalEntry(2, "B"));
    walWriter.append(makeWalEntry(3, "C"));

    var lateReader = walWriter.getReaderFromCurrent();

    walWriter.append(makeWalEntry(4, "D"));

    var batch = lateReader.readBatchAndAdvance(2);
    Assertions.assertEquals(List.of(4L), collectLsn(batch));
    Assertions.assertEquals(List.of("D"), collectPayloads(batch));
    Assertions.assertTrue(lateReader.readBatchAndAdvance(1).isEmpty());
  }

  public WalEntry makeWalEntry(long number, String cmd) {
    return new WalEntry(Lsn.fromNumber(number), cmd.getBytes());
  }

  public static WalManager.WalConfig sampleConfig() {
    return new WalManager.WalConfig(100L);
  }

  public static WalManager.WalConfig smallConfig() {
    return new WalManager.WalConfig(10L);
  }

  public static WalManager.WalConfig rotationConfig() {
    return new WalManager.WalConfig(50L);
  }

  private List<Long> collectLsn(List<WalEntry> entries) {
    return entries.stream().map(e -> e.getLsn().getNumber()).toList();
  }

  private List<String> collectPayloads(List<WalEntry> entries) {
    return entries.stream().map(e -> new String(e.getPayload())).toList();
  }
}

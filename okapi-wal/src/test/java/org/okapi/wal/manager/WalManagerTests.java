/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.wal.manager;

import java.io.IOException;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.okapi.wal.frame.WalEntry;
import org.okapi.wal.io.IllegalWalEntryException;
import org.okapi.wal.io.WalWriter;
import org.okapi.wal.lsn.Lsn;

public class WalManagerTests {
  @TempDir Path tempDir;

  @Test
  void testOnlyOneManagerPerDir() throws IOException {
    var walConfig = sampleConfig();
    var walManager = new WalManager(tempDir, walConfig);
    Assertions.assertThrows(
        OverlappingFileLockException.class, () -> new WalManager(tempDir, walConfig));
    walManager.close();
  }

  @Test
  void testOpenAfterClose() throws IOException {
    var walConfig = new WalManager.WalConfig(100L);
    var walManager = new WalManager(tempDir, walConfig);
    walManager.close();
    var secondWalManager = new WalManager(tempDir, walConfig);
    secondWalManager.close();
  }

  @Test
  void testNoRotationIfPreviousNotFull() throws IOException, IllegalWalEntryException {
    var walDir = new WalDirectory(tempDir);
    try (var walManager = new WalManager(tempDir, sampleConfig());
        var walWriter = new WalWriter(walManager, walDir)) {
      var entry1 = new WalEntry(Lsn.fromNumber(1), new byte[] {1});
      walWriter.append(entry1);

      // allocate again should reuse current segment because it is not full
      var samePath = walManager.allocateOrGetSegment();
      Assertions.assertEquals(0, walManager.getCurrentSegment());
      Assertions.assertEquals(walDir.getWalSegment(0), samePath);
    }

    // a 2nd WalManager starts on the next epoch
    try (var secondWalManager = new WalManager(tempDir, new WalManager.WalConfig(100L))) {
      var pathFromSecond = secondWalManager.allocateOrGetSegment();
      Assertions.assertEquals(1, secondWalManager.getCurrentSegment());
      Assertions.assertEquals(walDir.getWalSegment(1), pathFromSecond);
    }
  }

  @Test
  void testRotationIfPreviousFull() throws IOException, IllegalWalEntryException {
    var walDir = new WalDirectory(tempDir);
    try (var walManager = new WalManager(tempDir, new WalManager.WalConfig(50L));
        var walWriter = new WalWriter(walManager, walDir)) {
      // write a large entry to fill the first segment
      walWriter.append(new WalEntry(Lsn.fromNumber(1), new byte[60]));
      var nextPath = walManager.allocateOrGetSegment();
      Assertions.assertEquals(1, walManager.getCurrentSegment());
      Assertions.assertEquals(walDir.getWalSegment(1), nextPath);
    }
  }

  @Test
  void testRotationIfPreviousNotFull_existingSegment()
      throws IOException, IllegalWalEntryException {
    var walDir = new WalDirectory(tempDir);
    try (var walManager = new WalManager(tempDir, sampleConfig());
        var walWriter = new WalWriter(walManager, walDir)) {
      walWriter.append(new WalEntry(Lsn.fromNumber(1), new byte[] {1}));
    }

    try (var walManager = new WalManager(tempDir, sampleConfig())) {
      var path = walManager.allocateOrGetSegment();
      Assertions.assertEquals(1, walManager.getCurrentSegment());
      Assertions.assertEquals(walDir.getWalSegment(1), path);
    }
  }

  public static WalManager.WalConfig sampleConfig() {
    return new WalManager.WalConfig(100L);
  }

  @Test
  void testAllocAfterFullSegment() throws IOException, IllegalWalEntryException {
    var walDir = new WalDirectory(tempDir);
    try (var walManager = new WalManager(tempDir, sampleConfig())) {
      Assertions.assertEquals(0, walManager.getMaxLsnInSegment(0).get());
      var writer = new WalWriter(walManager, walDir);
      writer.append(new WalEntry(Lsn.fromNumber(20L), new byte[] {101}));
      walManager.allocateOrGetSegment();
      Assertions.assertEquals(20L, walManager.getMaxLsnInSegment(0).get());

      // check that segment file was flushed
      var segTableFile = walDir.getSegmentTable();
      Assertions.assertTrue(Files.exists(segTableFile));
    }

    try (var walManager = new WalManager(tempDir, sampleConfig())) {
      Assertions.assertEquals(20L, walManager.getMaxLsnInSegment(0).get());
    }
  }
}

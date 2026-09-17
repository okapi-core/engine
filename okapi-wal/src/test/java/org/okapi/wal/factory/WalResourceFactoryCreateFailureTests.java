/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.wal.factory;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.okapi.wal.commit.WalCommit;
import org.okapi.wal.frame.WalEntry;
import org.okapi.wal.io.IllegalWalEntryException;
import org.okapi.wal.io.WalWriter;
import org.okapi.wal.lsn.Lsn;
import org.okapi.wal.manager.WalDirectory;
import org.okapi.wal.manager.WalManager;

public class WalResourceFactoryCreateFailureTests {
  @TempDir Path dir;

  WalManager.WalConfig normalConfig = new WalManager.WalConfig(100L);

  @Test
  void commitAheadOfDataFails() throws IOException, IllegalWalEntryException {
    var walDir = new WalDirectory(dir);
    try (var walManager = new WalManager(walDir, normalConfig)) {
      var writer = new WalWriter(walManager, walDir);
      writer.append(new WalEntry(Lsn.fromNumber(10), "a".getBytes()));
    }
    // manually write a commit ahead of tip
    var gson = new Gson();
    var bogusCommit = gson.toJson(new WalCommit(Lsn.fromNumber(50)));
    Files.writeString(walDir.getWalCommit(), bogusCommit);

    var factory = new WalResourcesFactory(normalConfig);
    assertThrows(
        IllegalStateException.class,
        () -> factory.createResourcesWithFlushedLsn(dir, Lsn.fromNumber(0)));
  }

  @Test
  void flushedLsnBeyondDataFails() throws IOException, IllegalWalEntryException {
    var walDir = new WalDirectory(dir);
    try (var walManager = new WalManager(walDir, normalConfig)) {
      var writer = new WalWriter(walManager, walDir);
      writer.append(new WalEntry(Lsn.fromNumber(10), "a".getBytes()));
    }
    var factory = new WalResourcesFactory(normalConfig);
    assertThrows(
        IllegalStateException.class,
        () -> factory.createResourcesWithFlushedLsn(dir, Lsn.fromNumber(50)));
  }
}

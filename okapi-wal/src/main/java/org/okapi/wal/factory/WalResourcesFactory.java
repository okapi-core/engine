/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.wal.factory;

import java.io.IOException;
import java.nio.file.Path;
import lombok.AllArgsConstructor;
import org.okapi.wal.commit.WalCommit;
import org.okapi.wal.io.WalReader;
import org.okapi.wal.io.WalWriter;
import org.okapi.wal.lsn.Lsn;
import org.okapi.wal.lsn.MonoticLsnSupplier;
import org.okapi.wal.manager.WalDirectory;
import org.okapi.wal.manager.WalManager;

@AllArgsConstructor
public class WalResourcesFactory {
  WalManager.WalConfig walConfig;

  public WalResourceBundle createResourcesFromScratch(Path path) throws IOException {
    return createResourcesWithFlushedLsn(path, Lsn.fromNumber(0L));
  }

  public WalResourceBundle createResourcesWithFlushedLsn(Path path, Lsn lastFlushedLsn)
      throws IOException {
    var walDir = new WalDirectory(path);
    var walManager = new WalManager(path, walConfig);
    var walWriter = new WalWriter(walManager, walDir);
    var committedLsn = walManager.getCommittedLsn();
    if (committedLsn.isEmpty()) {
      walManager.commitLsn(lastFlushedLsn);
    } else {
      var commit = committedLsn.get();
      if (commit.getLsn().compareTo(lastFlushedLsn) < 0) {
        walManager.commitLsn(lastFlushedLsn);
      }
    }

    var latestCommit = walDir.getLatestCommit().map(WalCommit::getLsn).orElse(lastFlushedLsn);
    var walReader = new WalReader(walManager, walDir, latestCommit, walWriter::getLastWrittenLsn);
    var lsnSupplier = new MonoticLsnSupplier(walManager.getLastWrittenLsn().getNumber());
    return WalResourceBundle.builder()
        .manager(walManager)
        .writer(walWriter)
        .reader(walReader)
        .lsnSupplier(lsnSupplier)
        .build();
  }
}

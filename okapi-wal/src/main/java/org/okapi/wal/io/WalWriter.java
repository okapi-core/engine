/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.wal.io;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import lombok.Getter;
import lombok.Locked;
import org.okapi.wal.frame.WalEntry;
import org.okapi.wal.lsn.Lsn;
import org.okapi.wal.manager.WalDirectory;
import org.okapi.wal.manager.WalManager;

public class WalWriter implements Closeable {
  WalManager walManager;
  WalDirectory walDirectory;
  FileChannel fc;

  @Getter long bytesWrittenInCurSeg;
  @Getter Lsn lastWrittenLsn;
  ReadWriteLock walAppendLock;
  ByteBuffer walEntryLenBuff;

  public WalWriter(WalManager walManager, WalDirectory walDirectory) throws IOException {
    this.walManager = walManager;
    this.walAppendLock = new ReentrantReadWriteLock();
    var currentFile = walManager.allocateOrGetSegment();
    this.fc = openChannel(currentFile);
    this.walEntryLenBuff = ByteBuffer.allocateDirect(4);
    this.walDirectory = walDirectory;
    this.lastWrittenLsn = walManager.getLastWrittenLsn();
  }

  protected FileChannel openChannel(Path fp) throws IOException {
    var fc = FileChannel.open(fp, StandardOpenOption.WRITE);
    this.bytesWrittenInCurSeg = 0;
    return fc;
  }

  @Locked.Write("walAppendLock")
  public void append(WalEntry entry) throws IllegalWalEntryException, IOException {
    this.appendWithoutLock(entry);
  }

  private void appendWithoutLock(WalEntry entry) throws IllegalWalEntryException, IOException {
    if (entry.getLsn().compareTo(this.lastWrittenLsn) <= 0) {
      throw new IllegalWalEntryException(
          "Entry Lsn : " + entry.getLsn() + " is earlier than " + this.lastWrittenLsn);
    }
    var serialized = entry.serialize();
    this.walEntryLenBuff.clear();
    this.walEntryLenBuff.putInt(serialized.length);
    this.walEntryLenBuff.flip(); // reset position so the length prefix is written
    this.fc.write(this.walEntryLenBuff);
    this.fc.write(ByteBuffer.wrap(serialized));
    this.bytesWrittenInCurSeg += (4 + serialized.length);
    this.lastWrittenLsn = entry.getLsn();
    this.walManager.setLastWrittenLsn(entry.getLsn());
    if (bytesWrittenInCurSeg > walManager.getWalConfig().getSegmentSize()) {
      this.fc.close();
      var nextPath = walManager.allocateOrGetSegment();
      this.fc = openChannel(nextPath);
    }
  }

  public WalReader getReaderFromCurrent() throws IOException {
    return new WalReader(
        this.walManager, this.walDirectory, this.lastWrittenLsn, this::getLastWrittenLsn);
  }

  @Locked.Write("walAppendLock")
  public void appendBatch(Iterable<WalEntry> entries) throws IllegalWalEntryException, IOException {
    for (var entry : entries) {
      this.appendWithoutLock(entry);
    }
  }

  @Override
  public void close() throws IOException {
    this.fc.close();
  }
}

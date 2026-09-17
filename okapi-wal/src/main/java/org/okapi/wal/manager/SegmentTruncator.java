/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.wal.manager;

import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.okapi.wal.exceptions.CorruptedRecordException;
import org.okapi.wal.frame.WalEntry;
import org.okapi.wal.lsn.Lsn;

/**
 * Utility to repair partially-written WAL segments by truncating any trailing incomplete or corrupt
 * record. Returns the LSN of the last good entry (if any).
 */
public class SegmentTruncator {

  @AllArgsConstructor
  @Getter
  public static class TruncationResult {
    private final Optional<Lsn> lastGoodLsn;
    private final long truncatedToBytes;
  }

  public static TruncationResult truncate(Path segmentFile) throws IOException {
    Optional<Lsn> lastGood = Optional.empty();
    long truncateTo = 0L;
    try (var raf = new RandomAccessFile(segmentFile.toFile(), "rw")) {
      while (true) {
        truncateTo = raf.getFilePointer();
        int len;
        try {
          len = raf.readInt();
        } catch (EOFException eof) {
          break;
        }

        long remaining = raf.length() - raf.getFilePointer();
        if (len <= 0 || len > remaining) {
          break;
        }

        byte[] bytes = new byte[len];
        try {
          raf.readFully(bytes);
        } catch (EOFException eof) {
          break;
        }

        try {
          var walEntry = WalEntry.deserialize(bytes);
          lastGood = Optional.of(walEntry.getLsn());
          truncateTo = raf.getFilePointer();
        } catch (CorruptedRecordException e) {
          break;
        }
      }

      if (truncateTo < raf.length()) {
        raf.setLength(truncateTo);
      }
    }
    return new TruncationResult(lastGood, truncateTo);
  }
}

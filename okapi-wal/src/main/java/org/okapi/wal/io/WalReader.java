/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.wal.io;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.okapi.wal.frame.WalEntry;
import org.okapi.wal.lsn.Lsn;
import org.okapi.wal.manager.WalDirectory;
import org.okapi.wal.manager.WalManager;

public class WalReader {

  WalDirectory walDirectory;
  WalManager walManager;
  Supplier<Lsn> lastWrittenLsn;
  Lsn fromLsn;
  Lsn lastReadLsn;
  SegmentReader currentReader;

  WalEntry previous;

  public WalReader(
      WalManager walManager,
      WalDirectory walDirectory,
      Lsn fromLsn,
      Supplier<Lsn> lastWrittenLsnSupplier)
      throws IOException {
    this.walDirectory = walDirectory;
    this.fromLsn = fromLsn;
    this.walManager = walManager;
    this.lastWrittenLsn = lastWrittenLsnSupplier;
    var segmentContainingLsn = walManager.getSegmentContainingLsn(fromLsn);
    if (segmentContainingLsn.isEmpty()) {
      throw new IllegalStateException("Lsn larger than any found in the segments.");
    }
    this.lastReadLsn = Lsn.getStart();
    this.currentReader =
        new SegmentReader(
            segmentContainingLsn.get(), walDirectory.getWalSegment(segmentContainingLsn.get()));
  }

  public Optional<WalEntry> readNext() throws IOException {
    if (previous != null) {
      return Optional.of(previous);
    }
    var lastWritten = lastWrittenLsn.get();
    if (lastWritten.compareTo(fromLsn) < 0 || lastReadLsn == lastWritten) return Optional.empty();
    while (true) {
      var maxLsnInSegment =
          walManager.getMaxLsnInSegment(currentReader.getSegment()).orElse(Long.MAX_VALUE);
      if (this.lastReadLsn.getNumber() == maxLsnInSegment) {
        if (currentReader.getSegment() == walManager.getCurrentSegment()) {
          return Optional.empty();
        } else {
          currentReader.close();
          var nextSeg = walDirectory.getSegmentImmediatelyAfter(currentReader.getSegment());
          if (nextSeg.isEmpty()) return Optional.empty();
          var segFile = walDirectory.getWalSegment(nextSeg.get());
          currentReader = new SegmentReader(nextSeg.get(), segFile);
          continue;
        }
      }
      var entry = currentReader.readNextRecord();
      if (entry.isEmpty()) return Optional.empty();
      var walEntry = entry.get();
      lastReadLsn = walEntry.getLsn();
      if (lastReadLsn.compareTo(fromLsn) <= 0) {
        // skip entries prior to or at the requested start LSN
        continue;
      }
      previous = walEntry;
      return Optional.of(previous);
    }
  }

  public void advance() {
    previous = null;
  }

  public List<WalEntry> readBatchAndAdvance(int n) throws IOException {
    var batch = new ArrayList<WalEntry>();
    for (int i = 0; i < n; i++) {
      var entry = readNext();
      if (entry.isEmpty()) break;
      else batch.add(entry.get());
      advance();
    }
    return batch;
  }
}

/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.wal.io;

import java.io.Closeable;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import lombok.Getter;
import org.okapi.io.OkapiIo;
import org.okapi.io.StreamReadingException;
import org.okapi.wal.exceptions.CorruptedRecordException;
import org.okapi.wal.frame.WalEntry;

public class SegmentReader implements Closeable {

  Path segmentFile;
  FileInputStream fileInputStream;
  @Getter int segment;

  public SegmentReader(int segment, Path segmentFile) throws FileNotFoundException {
    this.segmentFile = segmentFile;
    this.fileInputStream = new FileInputStream(segmentFile.toFile());
    this.segment = segment;
  }

  public Optional<WalEntry> readNextRecord() {
    try {
      var entry = OkapiIo.readBytes(this.fileInputStream);
      return Optional.of(WalEntry.deserialize(entry));
    } catch (StreamReadingException e) {
      // EOF or truncated bytes; caller can stop iteration.
      return Optional.empty();
    } catch (IOException | CorruptedRecordException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void close() throws IOException {
    this.fileInputStream.close();
  }
}

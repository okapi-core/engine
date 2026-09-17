/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.wal.util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.okapi.wal.frame.WalEntry;

public final class WalTestWriteUtils {
  private WalTestWriteUtils() {}

  public static void writeEntry(Path path, WalEntry entry) throws IOException {
    var bytes = entry.serialize();
    Files.createDirectories(path.getParent());
    try (var fos =
        Files.newOutputStream(
            path, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
      var lenBuff = ByteBuffer.allocate(4).putInt(bytes.length);
      fos.write(lenBuff.array());
      fos.write(bytes);
    }
  }

  public static void writePartialEntry(Path path, WalEntry entry) throws IOException {
    var bytes = entry.serialize();
    int partialLen = bytes.length / 2;
    Files.createDirectories(path.getParent());
    try (var fos =
        Files.newOutputStream(
            path, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
      var lenBuff = ByteBuffer.allocate(4).putInt(bytes.length);
      fos.write(lenBuff.array());
      fos.write(bytes, 0, partialLen);
    }
  }
}

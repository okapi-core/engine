/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.wal.filelock;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

public class FileLockUtilsTest {
  @TempDir Path temp;

  @Test
  void doesNotBlockOnAlreadyLockedFile() throws IOException {
    var channel = Mockito.mock(FileChannel.class);
    Mockito.when(channel.lock()).thenReturn(null);
    assertThrows(FileLockException.class, () -> FileLockUtils.tryLockOrFail(channel));
  }
}

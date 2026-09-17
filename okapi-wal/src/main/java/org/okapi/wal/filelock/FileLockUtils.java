/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.wal.filelock;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import org.okapi.validation.OkapiChecks;

public class FileLockUtils {
  public static FileLock tryLockOrFail(FileChannel channel) throws IOException, FileLockException {
    var lock = channel.tryLock();
    OkapiChecks.checkArgument(lock != null, FileLockException::new);
    return lock;
  }
}

/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.checksums;

import com.google.common.hash.Hashing;
import com.google.common.io.Files;
import java.io.IOException;
import java.nio.file.Path;

public class ChecksumUtils {

  public static String getChecksum(Path path) throws IOException {
    // get md5 checksum of this path
    return Files.asByteSource(path.toFile()).hash(Hashing.murmur3_128()).toString();
  }
}

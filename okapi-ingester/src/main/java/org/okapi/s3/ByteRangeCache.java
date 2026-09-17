/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.s3;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import lombok.AllArgsConstructor;
import lombok.Getter;

public interface ByteRangeCache extends AutoCloseable, Closeable {

  byte[] getRange(String bucket, String key, long startInclusive, long endExclusive)
      throws IOException;

  default CompletableFuture<byte[]> getRangeAsync(
      String bucket, String key, long startInclusive, long endExclusive) {
    return CompletableFuture.supplyAsync(
        () -> {
          try {
            return getRange(bucket, key, startInclusive, endExclusive);
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        });
  }

  void invalidate(String bucket, String key);

  void invalidateAll();

  CacheStats getCacheStats();

  @Override
  void close();

  @Getter
  @AllArgsConstructor
  class CacheStats {
    private final long hits;
    private final long misses;
    private final long bytesFromCache;
    private final long bytesFetched;
    private final long evictions;
    private final long inflightCoalesced;
  }
}

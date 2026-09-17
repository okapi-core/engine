/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.metrics.ch;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.okapi.wal.manager.WalManager;

public class ChWalResourcesConcurrencyTest {
  @TempDir Path tempDir;

  @Test
  void appendPayloadsAssignsLsnsInAppendOrderUnderConcurrency() throws Exception {
    var resources = new ChWalResources(tempDir, new WalManager.WalConfig(1024 * 1024));
    var threadCount = 16;
    var appendsPerThread = 100;
    var start = new CountDownLatch(1);
    var executor = Executors.newFixedThreadPool(threadCount);
    var futures =
        java.util.stream.IntStream.range(0, threadCount)
            .mapToObj(
                thread ->
                    executor.submit(
                        () -> {
                          start.await();
                          for (int i = 0; i < appendsPerThread; i++) {
                            resources.appendPayloads(
                                List.of(("thread-" + thread + "-payload-" + i).getBytes()));
                          }
                          return null;
                        }))
            .toList();

    start.countDown();
    for (var future : futures) {
      assertDoesNotThrow(() -> future.get(10, TimeUnit.SECONDS));
    }
    executor.shutdownNow();

    assertEquals(
        threadCount * appendsPerThread, resources.getWriter().getLastWrittenLsn().getNumber());
  }
}

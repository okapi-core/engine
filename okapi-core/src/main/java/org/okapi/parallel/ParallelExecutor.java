/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.parallel;

import java.io.Closeable;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Supplier;

public class ParallelExecutor implements Closeable {
  int throttleLimit;
  ExecutorService executorService;
  Duration waitTime;

  public ParallelExecutor(int throttleLimit, int poolSize) {
    this.throttleLimit = throttleLimit;
    this.executorService = Executors.newFixedThreadPool(poolSize);
    this.waitTime = null;
  }

  public ParallelExecutor(int throttleLimit, int poolSize, Duration waitTime) {
    this.throttleLimit = throttleLimit;
    this.executorService = Executors.newFixedThreadPool(poolSize);
    this.waitTime = waitTime;
  }

  public <T> List<T> submit(List<Supplier<T>> suppliers, Duration waitTime) {
    var futures = new ArrayList<Future<T>>();
    var throttler = new Semaphore(throttleLimit);
    for (var supplier : suppliers) {
      var future =
          executorService.submit(
              () -> {
                throttler.acquire();
                var val = supplier.get();
                throttler.release();
                return val;
              });
      futures.add(future);
    }
    var results = new ArrayList<T>();
    try {
      for (var f : futures) {
        results.add(f.get(waitTime.toMillis(), TimeUnit.MILLISECONDS));
      }
      return results;
    } catch (ExecutionException | InterruptedException | TimeoutException e) {
      throw new RuntimeException(e);
    }
  }

  public <T> List<T> submit(List<Supplier<T>> suppliers) {
    if (waitTime == null) {
      throw new IllegalStateException("No default wait time configured.");
    }
    return submit(suppliers, waitTime);
  }

  @Override
  public void close() throws IOException {
    this.executorService.close();
  }
}

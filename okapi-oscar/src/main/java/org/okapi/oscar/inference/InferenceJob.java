/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.oscar.inference;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import lombok.Getter;

public class InferenceJob {
  @Getter String id;
  Callable<Void> inference;
  Supplier<Boolean> shouldCancel;

  @Getter Future<?> future;

  public InferenceJob(String id, Callable<Void> inference, Supplier<Boolean> shouldCancel) {
    this.id = id;
    this.inference = inference;
    this.shouldCancel = shouldCancel;
  }

  public void startWith(ExecutorService executorService) {
    this.future = executorService.submit(inference);
  }

  public void cancel() {
    if (this.future == null)
      throw new IllegalStateException("cancel called before job has started.");
    if (this.future.isDone()) return;
    this.future.cancel(false);
  }

  public boolean isDone() {
    return future != null && future.isDone();
  }
}

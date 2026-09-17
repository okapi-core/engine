/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.futures;

import java.util.Collection;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OkapiFutures {
  public static <T> void fireAndForgetWait(Collection<Future<T>> futures) {
    for (var f : futures) {
      try {
        f.get();
      } catch (InterruptedException | ExecutionException e) {
        log.error("Execution failed.", e);
      }
    }
  }
}

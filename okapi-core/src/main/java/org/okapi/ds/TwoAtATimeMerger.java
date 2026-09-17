/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.ds;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Optional;
import java.util.Queue;
import java.util.function.BiFunction;

public class TwoAtATimeMerger {
  public static <B> Optional<B> merge(Collection<B> base, BiFunction<B, B, B> mergeFn) {
    if (base.isEmpty()) return Optional.empty();
    if (base.size() == 1) {
      return base.stream().findFirst();
    }
    Queue<B> mergeables = new ArrayDeque<>(base);
    while (mergeables.size() != 1) {
      var top = mergeables.poll();
      var next = mergeables.poll();
      var merged = mergeFn.apply(top, next);
      mergeables.add(merged);
    }
    return Optional.of(mergeables.poll());
  }
}

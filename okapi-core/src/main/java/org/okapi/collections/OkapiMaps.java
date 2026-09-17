/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.collections;

import java.util.Map;
import java.util.function.Supplier;

public class OkapiMaps {
  public interface ClashResolver<T, V> {
    V resolve(T key, V existing, V newVal);
  }

  public static <T, V> Map<T, V> mergeMaps(
      Map<T, V> left,
      Map<T, V> right,
      ClashResolver<T, V> resolver,
      Supplier<Map<T, V>> newSupplier) {
    var merged = newSupplier.get();
    merged.putAll(left);
    for (var entry : right.entrySet()) {
      var key = entry.getKey();
      var value = entry.getValue();
      if (!merged.containsKey(key)) {
        merged.put(key, value);
      } else {
        var resolved = resolver.resolve(key, merged.get(key), value);
        merged.put(key, resolved);
      }
    }
    return merged;
  }
}

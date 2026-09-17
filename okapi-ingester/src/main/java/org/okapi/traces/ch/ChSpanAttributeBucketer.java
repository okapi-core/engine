/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.traces.ch;

import com.google.common.hash.Hashing;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

public final class ChSpanAttributeBucketer {
  public static final int BUCKETS = 10;
  private static final Set<String> RESERVED_KEYS = buildReservedKeys();

  private ChSpanAttributeBucketer() {}

  public static int bucketForKey(String key) {
    int hash = Hashing.murmur3_32_fixed().hashString(key, StandardCharsets.UTF_8).asInt();
    return Math.floorMod(hash, BUCKETS);
  }

  public static boolean isReservedKey(String key) {
    if (key == null || key.isEmpty()) return false;
    return RESERVED_KEYS.contains(key);
  }

  private static Set<String> buildReservedKeys() {
    var reserved = new HashSet<String>();
    reserved.addAll(ChSpansFallbackPaths.getAllPaths());
    return reserved;
  }
}

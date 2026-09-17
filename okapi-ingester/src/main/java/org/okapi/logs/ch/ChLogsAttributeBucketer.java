/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.logs.ch;

import com.google.common.hash.Hashing;
import java.nio.charset.StandardCharsets;
import java.util.Set;

public final class ChLogsAttributeBucketer {
  public static final int BUCKETS = 10;
  private static final Set<String> RESERVED_KEYS = Set.of("service.name", "log.stream");

  private ChLogsAttributeBucketer() {}

  public static int bucketForKey(String key) {
    int hash = Hashing.murmur3_32_fixed().hashString(key, StandardCharsets.UTF_8).asInt();
    return Math.floorMod(hash, BUCKETS);
  }

  public static boolean isReservedKey(String key) {
    if (key == null || key.isEmpty()) {
      return false;
    }
    return RESERVED_KEYS.contains(key);
  }
}

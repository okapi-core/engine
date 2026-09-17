/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.engine.index;

public final class ShardHashFns {
  private ShardHashFns() {}

  public static ShardHashFn forStrategy(HashStrategy strategy) {
    if (strategy == null) {
      throw new IllegalArgumentException("strategy cannot be null");
    }
    return switch (strategy) {
      case WINDOW_MOD ->
          (window, nShards) -> {
            if (nShards <= 0) {
              throw new IllegalArgumentException("nShards must be positive");
            }
            return Math.floorMod(window, nShards);
          };
    };
  }
}

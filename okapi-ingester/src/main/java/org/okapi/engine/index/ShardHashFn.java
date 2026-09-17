/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.engine.index;

public interface ShardHashFn {
  int shardFor(long window, int nShards);
}

/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.engine.index;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryIndexRegistry implements IndexRegistry {
  private final ConcurrentMap<String, IndexConfig> configs = new ConcurrentHashMap<>();

  public InMemoryIndexRegistry() {}

  public InMemoryIndexRegistry(Map<String, IndexConfig> configs) {
    if (configs == null) {
      throw new IllegalArgumentException("configs cannot be null");
    }
    configs.values().forEach(this::put);
  }

  public void put(IndexConfig config) {
    if (config == null) {
      throw new IllegalArgumentException("config cannot be null");
    }
    configs.put(config.indexId(), config);
  }

  @Override
  public IndexConfig getIndexConfig(String indexId) {
    if (indexId == null || indexId.isBlank()) {
      throw new IllegalArgumentException("indexId cannot be null or blank");
    }
    var config = configs.get(indexId);
    if (config == null) {
      throw new IllegalArgumentException("Unknown index: " + indexId);
    }
    return config;
  }
}

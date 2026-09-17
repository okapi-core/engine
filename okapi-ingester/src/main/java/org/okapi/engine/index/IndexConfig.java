/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.engine.index;

public record IndexConfig(
    String indexId,
    int nShards,
    int nReplicas,
    HashStrategy hashStrategy,
    long windowSizeMillis,
    long diskRetentionPeriodMillis) {
  public IndexConfig {
    if (indexId == null || indexId.isBlank()) {
      throw new IllegalArgumentException("indexId cannot be null or blank");
    }
    if (nShards <= 0) {
      throw new IllegalArgumentException("nShards must be positive");
    }
    if (nReplicas < 0) {
      throw new IllegalArgumentException("nReplicas cannot be negative");
    }
    if (hashStrategy == null) {
      throw new IllegalArgumentException("hashStrategy cannot be null");
    }
    if (windowSizeMillis <= 0) {
      throw new IllegalArgumentException("windowSizeMillis must be positive");
    }
    if (diskRetentionPeriodMillis < 0) {
      throw new IllegalArgumentException("diskRetentionPeriodMillis cannot be negative");
    }
  }
}

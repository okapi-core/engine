/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.metrics.pojos;

import java.util.Optional;
import lombok.Getter;

public enum AGG_TYPE {
  AVG("avg"),
  SUM("sum"),
  MIN("min"),
  MAX("max"),
  COUNT("count"),
  P50("p50"),
  P75("p75"),
  P90("p90"),
  P95("p95"),
  P99("p99");

  @Getter private String mergeFunction;

  AGG_TYPE(String mergeFunction) {
    this.mergeFunction = mergeFunction;
  }

  public static Optional<AGG_TYPE> parse(String v) {
    if (v == null || v.isEmpty()) {
      return Optional.empty();
    }
    for (AGG_TYPE aggType : AGG_TYPE.values()) {
      if (aggType.getMergeFunction().equalsIgnoreCase(v)) {
        return Optional.of(aggType);
      }
    }
    return Optional.empty();
  }
}

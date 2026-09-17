/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.metrics.pojos;

import java.util.Optional;
import lombok.Getter;

public enum RES_TYPE {
  SECONDLY("s"),
  MINUTELY("m"),
  HOURLY("h");

  @Getter private String resolution;

  RES_TYPE(String resolution) {
    this.resolution = resolution;
  }

  public static Optional<RES_TYPE> parse(String v) {
    if (v == null || v.isEmpty()) {
      return Optional.empty();
    }
    for (RES_TYPE resType : RES_TYPE.values()) {
      if (resType.getResolution().equalsIgnoreCase(v)) {
        return Optional.of(resType);
      }
    }
    return Optional.empty();
  }
}

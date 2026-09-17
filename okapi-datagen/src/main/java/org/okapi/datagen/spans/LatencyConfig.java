/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.datagen.spans;

import java.util.Random;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class LatencyConfig {
  long minMs;
  long maxMs;
  long timeoutPenaltyMs;

  public long sampleMs(Random random) {
    if (maxMs <= minMs) {
      return minMs;
    }
    long diff = maxMs - minMs;
    return minMs + (Math.abs(random.nextLong()) % (diff + 1));
  }
}

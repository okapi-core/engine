/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.ids;

import java.security.SecureRandom;
import java.util.UUID;

public final class UuidV7 {
  private static final SecureRandom RNG = new SecureRandom();

  private UuidV7() {}

  public static UUID randomUuid() {
    long timestamp = System.currentTimeMillis() & 0xFFFFFFFFFFFFL;
    long rand12 = RNG.nextInt(1 << 12) & 0xFFFL;
    long rand62 = RNG.nextLong() & 0x3FFFFFFFFFFFFFFFL;

    long mostSigBits = (timestamp << 16) | (0x7L << 12) | rand12;
    long leastSigBits = (rand62) | 0x8000000000000000L;

    return new UUID(mostSigBits, leastSigBits);
  }
}

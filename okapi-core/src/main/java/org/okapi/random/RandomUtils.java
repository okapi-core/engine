/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.random;

import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public class RandomUtils {
  private static final char[] RANDOM_STRING_ALPHABET =
      "abcdefghijklmnopqrstuvwxyz0123456789".toCharArray();

  public static <T> T getWeightedRandomSample(
      List<T> collection, List<Double> weights, Random random) {
    if (collection.isEmpty() || weights.size() != collection.size())
      throw new IllegalArgumentException();
    var csum = new double[collection.size() + 1];
    var total = weights.stream().reduce(Double::sum).orElse(0.);
    var weight = random.nextDouble() * total;
    for (int i = 0; i < collection.size(); i++) {
      csum[i + 1] = csum[i] + weights.get(i);
      if (csum[i + 1] > weight) {
        return collection.get(i);
      }
    }
    return collection.getFirst();
  }

  public static byte[] randomBytes(Random random, int size) {
    var bytes = new byte[size];
    random.nextBytes(bytes);
    return bytes;
  }

  public static byte[] randomOtelTraceId(Random random) {
    return randomBytes(random, 16);
  }

  public static byte[] getRanomOtelSpanId(Random random) {
    return randomBytes(random, 8);
  }

  public static String randomString(int len) {
    if (len < 0) {
      throw new IllegalArgumentException("len must be non-negative");
    }
    var random = ThreadLocalRandom.current();
    var chars = new char[len];
    for (int i = 0; i < len; i++) {
      chars[i] = RANDOM_STRING_ALPHABET[random.nextInt(RANDOM_STRING_ALPHABET.length)];
    }
    return new String(chars);
  }
}

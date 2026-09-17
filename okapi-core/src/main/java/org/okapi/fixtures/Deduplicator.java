/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.fixtures;

import org.okapi.random.RandomUtils;

public class Deduplicator {
  private static final String SESSION_PREFIX;

  static {
    SESSION_PREFIX = RandomUtils.randomString(8);
  }

  public static <T> String dedup(String input, String testInstance, Class<T> clazz) {
    return clazz.getSimpleName() + "_" + testInstance.replace("-", "") + input;
  }

  public static <T> String dedupWithSession(String input, String testInstance, Class<T> clazz) {
    return SESSION_PREFIX + "_" + dedup(input, testInstance, clazz);
  }
}

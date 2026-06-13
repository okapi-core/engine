/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.pg;

public final class PgKeys {
  private PgKeys() {}

  public static String key(String... values) {
    return String.join("\u001f", values);
  }
}

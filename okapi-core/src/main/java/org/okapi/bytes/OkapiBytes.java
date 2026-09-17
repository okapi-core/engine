/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.bytes;

import java.util.HexFormat;

public class OkapiBytes {
  public static String encodeAsHex(byte[] bytes) {
    return HexFormat.of().formatHex(bytes);
  }

  public static byte[] decodeHex(CharSequence bytes) {
    return HexFormat.of().parseHex(bytes);
  }
}

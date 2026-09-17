/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.arrays;

public class ArrayUtils {

  public static byte[] concatArrays(byte[] a, byte[] b) {
    var out = new byte[a.length + b.length];
    System.arraycopy(a, 0, out, 0, a.length);
    System.arraycopy(b, 0, out, a.length, b.length);
    return out;
  }
}

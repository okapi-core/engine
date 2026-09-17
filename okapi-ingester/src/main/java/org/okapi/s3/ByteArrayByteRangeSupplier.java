/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.s3;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class ByteArrayByteRangeSupplier implements ByteRangeSupplier {

  byte[] bytes;

  @Override
  public byte[] getBytes(long start, int len) {
    var buf = new byte[len];
    System.arraycopy(bytes, (int) start, buf, 0, len);
    return buf;
  }

  @Override
  public long getEnd() {
    return bytes.length;
  }
}

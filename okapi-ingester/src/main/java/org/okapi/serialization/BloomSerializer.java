/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.serialization;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class BloomSerializer {
  public static byte[] serializeBloom(BloomFilter<?> filter) throws IOException {
    var boas = new ByteArrayOutputStream();
    filter.writeTo(boas);
    return boas.toByteArray();
  }

  public static BloomFilter<Integer> deserializeIntBloom(byte[] sec) throws IOException {
    return BloomFilter.readFrom(new ByteArrayInputStream(sec), Funnels.integerFunnel());
  }

  public static BloomFilter<CharSequence> deserializeStringBloom(byte[] sec) throws IOException {
    return BloomFilter.readFrom(
        new ByteArrayInputStream(sec), Funnels.stringFunnel(StandardCharsets.UTF_8));
  }

  public static BloomFilter<byte[]> deserializeByteArrayBloom(byte[] sec) throws IOException {
    return BloomFilter.readFrom(new ByteArrayInputStream(sec), Funnels.byteArrayFunnel());
  }
}

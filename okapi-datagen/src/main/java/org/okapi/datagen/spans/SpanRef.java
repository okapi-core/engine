/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.datagen.spans;

import com.google.protobuf.ByteString;
import lombok.Value;

@Value
public class SpanRef {
  byte[] spanId;
  byte[] traceId;

  public ByteString spanIdBytes() {
    return ByteString.copyFrom(spanId);
  }

  public ByteString traceIdBytes() {
    return ByteString.copyFrom(traceId);
  }
}

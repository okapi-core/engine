/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.otelshorthand;

import com.google.protobuf.ByteString;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.KeyValue;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public final class OtelShortHands {
  private OtelShortHands() {}

  public static KeyValue keyValue(String key, String value) {
    return KeyValue.newBuilder()
        .setKey(key)
        .setValue(AnyValue.newBuilder().setStringValue(value))
        .build();
  }

  public static KeyValue keyValue(String key, int value) {
    return KeyValue.newBuilder()
        .setKey(key)
        .setValue(AnyValue.newBuilder().setIntValue(value))
        .build();
  }

  public static KeyValue keyValue(String key, double value) {
    return KeyValue.newBuilder()
        .setKey(key)
        .setValue(AnyValue.newBuilder().setDoubleValue(value))
        .build();
  }

  public static List<KeyValue> keyValues(Map<String, String> vals) {
    return vals.entrySet().stream()
        .map(entry -> keyValue(entry.getKey(), entry.getValue()))
        .toList();
  }

  public static KeyValue keyValue(String key, boolean value) {
    return KeyValue.newBuilder()
        .setKey(key)
        .setValue(AnyValue.newBuilder().setBoolValue(value))
        .build();
  }

  public static AnyValue strValue(String s) {
    return AnyValue.newBuilder().setStringValue(s).build();
  }

  public static AnyValue intValue(int x) {
    return AnyValue.newBuilder().setIntValue(x).build();
  }

  public static ByteString utf8Bytes(String value) {
    return ByteString.copyFrom(value.getBytes(StandardCharsets.UTF_8));
  }
}

/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.datagen.spans;

import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.KeyValue;
import java.util.List;
import java.util.Map;

public final class OtelShorthand {
  private OtelShorthand() {}

  public static AnyValue anyString(String value) {
    return AnyValue.newBuilder().setStringValue(value).build();
  }

  public static KeyValue kv(String key, String value) {
    return KeyValue.newBuilder().setKey(key).setValue(anyString(value)).build();
  }

  public static KeyValue kvInt(String key, int value) {
    return KeyValue.newBuilder()
        .setKey(key)
        .setValue(AnyValue.newBuilder().setIntValue(value).build())
        .build();
  }

  public static KeyValue kvDouble(String key, double value) {
    return KeyValue.newBuilder()
        .setKey(key)
        .setValue(AnyValue.newBuilder().setDoubleValue(value).build())
        .build();
  }

  public static List<KeyValue> toKvList(Map<String, String> attrs) {
    return attrs.entrySet().stream().map(e -> kv(e.getKey(), e.getValue())).toList();
  }
}

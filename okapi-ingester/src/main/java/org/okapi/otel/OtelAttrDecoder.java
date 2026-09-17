/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.otel;

import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.KeyValue;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class OtelAttrDecoder {
  private OtelAttrDecoder() {}

  public static Optional<String> getStringStrict(
      Map<String, AnyValue> attrs, String key, List<String> fallbacks) {
    return getAttribute(attrs, key, fallbacks).flatMap(OtelAnyValueDecoder::decodeAsString);
  }

  public static Optional<Integer> getIntStrict(
      Map<String, AnyValue> attrs, String key, List<String> fallbacks) {
    return getAttribute(attrs, key, fallbacks).flatMap(OtelAnyValueDecoder::decodeAsInt);
  }

  public static Optional<String> getStringStrict(Map<String, AnyValue> attrs, List<String> paths) {
    return getAttribute(attrs, paths).flatMap(OtelAnyValueDecoder::decodeAsString);
  }

  public static Optional<Integer> getIntStrict(Map<String, AnyValue> attrs, List<String> paths) {
    return getAttribute(attrs, paths).flatMap(OtelAnyValueDecoder::decodeAsInt);
  }

  public static Optional<AnyValue> getAttribute(
      Map<String, AnyValue> attrs, String key, List<String> fallbacks) {
    if (attrs.containsKey(key)) {
      return Optional.of(attrs.get(key));
    }
    for (String fallback : fallbacks) {
      if (attrs.containsKey(fallback)) {
        return Optional.of(attrs.get(fallback));
      }
    }
    return Optional.empty();
  }

  public static Optional<AnyValue> getAttribute(Map<String, AnyValue> attrs, List<String> paths) {
    if (paths == null || paths.isEmpty()) return Optional.empty();
    var primary = paths.getFirst();
    var fallbacks =
        paths.size() > 1 ? paths.subList(1, paths.size()) : Collections.<String>emptyList();
    return getAttribute(attrs, primary, fallbacks);
  }

  public static Map<String, AnyValue> toAttrMap(List<KeyValue> attrs) {
    if (attrs == null || attrs.isEmpty()) return Collections.emptyMap();
    Map<String, AnyValue> out = new HashMap<>(attrs.size());
    for (KeyValue kv : attrs) {
      if (kv == null || kv.getKey().isEmpty()) continue;
      out.put(kv.getKey(), kv.getValue());
    }
    return out;
  }
}

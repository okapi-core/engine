/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.otel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.KeyValue;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class OtelAttrDecoderTests {

  @Test
  public void getAttribute_supportsFallbacks() {
    var primary = AnyValue.newBuilder().setStringValue("primary").build();
    var fallback = AnyValue.newBuilder().setStringValue("fallback").build();
    Map<String, AnyValue> attrs = Map.of("key", primary, "fallback.key", fallback);

    assertEquals(
        primary, OtelAttrDecoder.getAttribute(attrs, "key", List.of("fallback.key")).orElseThrow());
    assertEquals(
        fallback,
        OtelAttrDecoder.getAttribute(attrs, "missing", List.of("fallback.key")).orElseThrow());
  }

  @Test
  public void getAttribute_withPathsReturnsEmptyForEmpty() {
    assertFalse(OtelAttrDecoder.getAttribute(Map.of(), List.of()).isPresent());
  }

  @Test
  public void getStringStrict_usesFallbacks() {
    var fallback = AnyValue.newBuilder().setStringValue("fallback").build();
    Map<String, AnyValue> attrs = Map.of("fallback.key", fallback);

    var result = OtelAttrDecoder.getStringStrict(attrs, "missing", List.of("fallback.key"));
    assertTrue(result.isPresent());
    assertEquals("fallback", result.get());
  }

  @Test
  public void getIntStrict_usesFallbacks() {
    var fallback = AnyValue.newBuilder().setIntValue(99).build();
    Map<String, AnyValue> attrs = Map.of("fallback.key", fallback);

    var result = OtelAttrDecoder.getIntStrict(attrs, "missing", List.of("fallback.key"));
    assertTrue(result.isPresent());
    assertEquals(99, result.get());
  }

  @Test
  public void getStringStrict_withPaths() {
    var v = AnyValue.newBuilder().setStringValue("value").build();
    Map<String, AnyValue> attrs = Map.of("path", v);

    var result = OtelAttrDecoder.getStringStrict(attrs, List.of("path"));
    assertTrue(result.isPresent());
    assertEquals("value", result.get());
  }

  @Test
  public void getIntStrict_withPaths() {
    var v = AnyValue.newBuilder().setIntValue(7).build();
    Map<String, AnyValue> attrs = Map.of("path", v);

    var result = OtelAttrDecoder.getIntStrict(attrs, List.of("path"));
    assertTrue(result.isPresent());
    assertEquals(7, result.get());
  }

  @Test
  public void getStringStrict_rejectsNonStringTypes() {
    var v = AnyValue.newBuilder().setIntValue(7).build();
    Map<String, AnyValue> attrs = Map.of("path", v);

    assertFalse(OtelAttrDecoder.getStringStrict(attrs, List.of("path")).isPresent());
  }

  @Test
  public void getIntStrict_rejectsNonNumericTypes() {
    var v = AnyValue.newBuilder().setBoolValue(true).build();
    Map<String, AnyValue> attrs = Map.of("path", v);

    assertFalse(OtelAttrDecoder.getIntStrict(attrs, List.of("path")).isPresent());
  }

  @Test
  public void getAttribute_supportsPathList() {
    var v = AnyValue.newBuilder().setStringValue("value").build();
    Map<String, AnyValue> attrs = Map.of("a", v);

    assertEquals(v, OtelAttrDecoder.getAttribute(attrs, List.of("a", "b")).orElseThrow());
  }

  @Test
  public void toAttrMap_skipsEmptyKeys() {
    var valid =
        KeyValue.newBuilder()
            .setKey("k")
            .setValue(AnyValue.newBuilder().setStringValue("v"))
            .build();
    var emptyKey =
        KeyValue.newBuilder()
            .setKey("")
            .setValue(AnyValue.newBuilder().setStringValue("x"))
            .build();

    var map = OtelAttrDecoder.toAttrMap(List.of(valid, emptyKey));
    assertEquals(1, map.size());
    assertTrue(map.containsKey("k"));
  }
}

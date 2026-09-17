/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.otel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.opentelemetry.proto.common.v1.AnyValue;
import org.junit.jupiter.api.Test;

public class OtelAnyValueDecoderTests {

  @Test
  public void decodeAsString_acceptsOnlyStringValues() {
    var stringVal = AnyValue.newBuilder().setStringValue("hello").build();
    var intVal = AnyValue.newBuilder().setIntValue(42).build();

    var decoded = OtelAnyValueDecoder.decodeAsString(stringVal);
    assertTrue(decoded.isPresent());
    assertEquals("hello", decoded.get());

    assertFalse(OtelAnyValueDecoder.decodeAsString(intVal).isPresent());
  }

  @Test
  public void decodeAsInt_acceptsIntDoubleAndNumericString() {
    var intVal = AnyValue.newBuilder().setIntValue(42).build();
    var doubleVal = AnyValue.newBuilder().setDoubleValue(12.7).build();
    var stringVal = AnyValue.newBuilder().setStringValue("123").build();

    assertEquals(42, OtelAnyValueDecoder.decodeAsInt(intVal).orElseThrow());
    assertEquals(13, OtelAnyValueDecoder.decodeAsInt(doubleVal).orElseThrow());
    assertEquals(123, OtelAnyValueDecoder.decodeAsInt(stringVal).orElseThrow());
  }

  @Test
  public void decodeAsInt_rejectsNonNumericString() {
    var stringVal = AnyValue.newBuilder().setStringValue("abc").build();
    assertFalse(OtelAnyValueDecoder.decodeAsInt(stringVal).isPresent());
  }

  @Test
  public void decodeAsInt_rejectsUnsupportedTypes() {
    var boolVal = AnyValue.newBuilder().setBoolValue(true).build();
    assertFalse(OtelAnyValueDecoder.decodeAsInt(boolVal).isPresent());
  }
}

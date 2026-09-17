/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.otelshorthand;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class OtelShortHandsTest {

  @Test
  public void testStrValue() {
    assertEquals("str", OtelShortHands.strValue("str").getStringValue());
    assertEquals("", OtelShortHands.strValue("").getStringValue());
  }

  @Test
  public void testIntValue() {
    assertEquals(10, OtelShortHands.intValue(10).getIntValue());
    assertEquals(Integer.MAX_VALUE, OtelShortHands.intValue(Integer.MAX_VALUE).getIntValue());
    assertEquals(Integer.MIN_VALUE, OtelShortHands.intValue(Integer.MIN_VALUE).getIntValue());
    assertEquals(-1, OtelShortHands.intValue(-1).getIntValue());
  }
}

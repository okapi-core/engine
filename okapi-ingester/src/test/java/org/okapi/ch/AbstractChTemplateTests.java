/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.ch;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

public class AbstractChTemplateTests {

  @Test
  void mapBuiltCorrectly() {
    var template = new AbstractChTemplate();
    assertEquals("map('a','b')", template.asMap(Map.of("a", "b")));
  }

  @Test
  void mapWithMultiple() {
    var template = new AbstractChTemplate();
    assertEquals("map('a','b','c','d')", template.asMap(new TreeMap<>(Map.of("a", "b", "c", "d"))));
  }
}

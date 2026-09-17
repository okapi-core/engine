/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class OkapiMapsTest {

  @Test
  void mergeSingle() {
    var a = Map.of("A", "vA");
    var b = Map.<String, String>of();
    var mergedAb = OkapiMaps.mergeMaps(a, b, (x, y, z) -> "nope", HashMap::new);
    assertEquals(Map.of("A", "vA"), mergedAb);
    var mergedBa = OkapiMaps.mergeMaps(b, a, (x, y, z) -> "nope", HashMap::new);
    assertEquals(Map.of("A", "vA"), mergedBa);
  }

  @Test
  void mergeWithoutClash() {
    var a = Map.of("A", "vA");
    var b = Map.<String, String>of("B", "vB");
    var mergedAb = OkapiMaps.mergeMaps(a, b, (x, y, z) -> "nope", HashMap::new);
    assertEquals(Map.of("A", "vA", "B", "vB"), mergedAb);

    var mergedBa = OkapiMaps.mergeMaps(b, a, (x, y, z) -> "nope", HashMap::new);
    assertEquals(Map.of("A", "vA", "B", "vB"), mergedBa);
  }

  @Test
  void mergeWithClash() {
    var a = Map.of("A", "vA");
    var b = Map.<String, String>of("A", "vB");
    var mergedAb = OkapiMaps.mergeMaps(a, b, (x, y, z) -> "nope", HashMap::new);
    assertEquals(Map.of("A", "nope"), mergedAb);
    var mergedBa = OkapiMaps.mergeMaps(a, b, (x, y, z) -> "nope", HashMap::new);
    assertEquals(Map.of("A", "nope"), mergedBa);
  }
}

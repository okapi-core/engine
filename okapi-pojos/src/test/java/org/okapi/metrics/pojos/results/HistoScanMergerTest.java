/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.metrics.pojos.results;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

public class HistoScanMergerTest {

  @Test
  void merge_sameSchema_elementwiseSum() {
    var ubs = List.of(100f, 200f, 500f);
    var h1 = new HistoScan("p", 0, 10, ubs, List.of(1, 2, 3, 4));
    var h2 = new HistoScan("p", 2, 12, ubs, List.of(5, 6, 7, 8));

    var merged = HistoScanMerger.merge("p", List.of(h1, h2));
    assertEquals(ubs, merged.getUbs());
    assertEquals(List.of(6, 8, 10, 12), merged.getCounts());
    assertEquals(0, merged.getStart());
    assertEquals(12, merged.getEnd());
  }

  @Test
  void merge_differentSchema_cdfMixture() {
    var h1 = new HistoScan("p", 0, 10, List.of(100f, 200f), List.of(10, 10, 0));
    var h2 = new HistoScan("p", 0, 10, List.of(150f, 250f), List.of(0, 10, 10));

    var merged = HistoScanMerger.merge("p", List.of(h1, h2));
    // Target bounds = union {100,200,150,250} -> [100,150,200,250]
    assertEquals(List.of(100f, 150f, 200f, 250f), merged.getUbs());
    int total = merged.getCounts().stream().mapToInt(i -> i).sum();
    assertEquals(40, total);
    // Expect non-zero in head/interior reflecting redistribution; sanity check monotonic CDF via
    // non-negative counts
    for (int c : merged.getCounts()) assertTrue(c >= 0);
  }

  @Test
  void merge_empty() {
    var merged = HistoScanMerger.merge("p", List.of());
    assertTrue(merged.getUbs().isEmpty());
    assertTrue(merged.getCounts().isEmpty());
  }
}

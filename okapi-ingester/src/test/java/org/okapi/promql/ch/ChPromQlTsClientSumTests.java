/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.ch;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class ChPromQlTsClientSumTests {
  @Test
  void sumScansPreserveFractionalDeltaValues() {
    var scan =
        ChPromQlTsClient.toSumScan(
            "requests",
            List.of(
                new ChPromQlTsClient.SumPoint(0L, 1L, 1.25d),
                new ChPromQlTsClient.SumPoint(1L, 2L, 2.5d)),
            false);

    assertEquals(List.of(1.25d, 2.5d), scan.getCounts());
  }

  @Test
  void cumulativeSumScansConvertToFractionalDeltasAndHandleResets() {
    var scan =
        ChPromQlTsClient.toSumScan(
            "requests",
            List.of(
                new ChPromQlTsClient.SumPoint(0L, 1L, 1.25d),
                new ChPromQlTsClient.SumPoint(1L, 2L, 3.75d),
                new ChPromQlTsClient.SumPoint(2L, 3L, 0.5d)),
            true);

    assertEquals(List.of(1.25d, 2.5d, 0.5d), scan.getCounts());
  }
}

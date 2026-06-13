/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.ch;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CreateChTablesSpecTests {
  @Test
  void rawPromQlNumericSamplesUseFloat64Storage() {
    assertTrue(CreateChTablesSpec.getCreateGaugeTableSpec().contains("value Float64"));
    assertTrue(CreateChTablesSpec.getCreateSumTableSpec().contains("value Float64"));
  }
}

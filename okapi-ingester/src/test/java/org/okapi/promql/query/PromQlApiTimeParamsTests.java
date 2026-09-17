/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.okapi.exceptions.BadRequestException;

public class PromQlApiTimeParamsTests {

  @Test
  void parsesRequiredTime() throws BadRequestException {
    assertEquals(1_500L, PromQlApiTimeParams.requiredTime("1.5", "start-date"));
    assertEquals(
        1_672_531_200_000L, PromQlApiTimeParams.requiredTime("2023-01-01T00:00:00Z", "start-date"));
  }

  @Test
  void rejectsInvalidRequiredTime() {
    var error =
        assertThrows(
            BadRequestException.class,
            () -> PromQlApiTimeParams.requiredTime("nope", "start-date"));
    assertEquals("Date: nope is not a valid start-date", error.getMessage());
  }

  @Test
  void parsesOptionalTimeWithDefault() throws BadRequestException {
    assertEquals(42L, PromQlApiTimeParams.optionalTime(null, 42L));
    assertEquals(2_000L, PromQlApiTimeParams.optionalTime("2", 42L));
  }

  @Test
  void parsesRequiredStepMillis() throws BadRequestException {
    assertEquals(15_000L, PromQlApiTimeParams.requiredStepMillis("15"));
    assertEquals(300_000L, PromQlApiTimeParams.requiredStepMillis("5m"));
  }

  @Test
  void parsesOptionalRange() throws BadRequestException {
    var range = PromQlApiTimeParams.optionalRange("1", "2");
    assertEquals(1_000L, range.getStartMs());
    assertEquals(2_000L, range.getEndMs());
  }
}

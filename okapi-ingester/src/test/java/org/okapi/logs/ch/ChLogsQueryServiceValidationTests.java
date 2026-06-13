/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.logs.ch;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.okapi.exceptions.BadRequestException;
import org.okapi.rest.logs.ChLogFilter;
import org.okapi.rest.logs.ChLogFilterField;
import org.okapi.rest.logs.ChLogsQueryRequest;

class ChLogsQueryServiceValidationTests {
  private final ChLogsQueryService service = new ChLogsQueryService(null, null);

  @Test
  void rejectsNullRequest() {
    assertThrows(BadRequestException.class, () -> service.getLogs(null));
  }

  @Test
  void rejectsNonPositiveLimit() {
    assertThrows(
        BadRequestException.class,
        () -> service.getLogs(ChLogsQueryRequest.builder().limit(0).build()));
    assertThrows(
        BadRequestException.class,
        () -> service.getLogs(ChLogsQueryRequest.builder().limit(-1).build()));
  }

  @Test
  void rejectsFilterWithoutField() {
    var request =
        ChLogsQueryRequest.builder().filters(java.util.List.of(ChLogFilter.builder().build())).build();

    assertThrows(BadRequestException.class, () -> service.getLogs(request));
  }

  @Test
  void rejectsLevelFilterWithoutLevel() {
    var request =
        ChLogsQueryRequest.builder()
            .filters(java.util.List.of(ChLogFilter.builder().field(ChLogFilterField.LOG_LEVEL).build()))
            .build();

    assertThrows(BadRequestException.class, () -> service.getLogs(request));
  }

  @Test
  void rejectsStringFilterWithoutValue() {
    var request =
        ChLogsQueryRequest.builder()
            .filters(
                java.util.List.of(
                    ChLogFilter.builder().field(ChLogFilterField.SERVICE_NAME).build()))
            .build();

    assertThrows(BadRequestException.class, () -> service.getLogs(request));
  }
}

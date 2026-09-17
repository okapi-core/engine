/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.logs;

import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@AllArgsConstructor
@Builder
@Getter
@NoArgsConstructor
@ToString
public class ChLogsFieldsRequest {
  Long tsStartNanos;
  Long tsEndNanos;
  String queryPrefix;

  @Min(value = 1, message = "limit must be greater than zero")
  Integer limit;
}

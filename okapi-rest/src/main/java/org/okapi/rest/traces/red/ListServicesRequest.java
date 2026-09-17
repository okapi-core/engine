/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.traces.red;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.okapi.rest.traces.TimestampFilter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
public class ListServicesRequest {
  @NotNull(message = "time window must be provided")
  TimestampFilter timestampFilter;
}

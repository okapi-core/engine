/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.chat;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class GetHistoryRequest {
  static final GetHistoryRequest START_OF_TIME = GetHistoryRequest.builder().from(0L).build();
  @NotNull Long from;
  Long to;

  public static GetHistoryRequest fromStart() {
    return START_OF_TIME;
  }
}

/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.session;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CreateSessionBlindRequest {
  @NotNull String initialMsg;
}

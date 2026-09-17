/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.dtos.auth;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class TokenResponse {
  @NotNull String token;
}

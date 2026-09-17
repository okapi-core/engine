/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.dtos.auth;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SignInRequest {
  @NotNull(message = "Email must be provided.")
  String email;

  @NotNull(message = "Password must be provided.")
  String password;
}

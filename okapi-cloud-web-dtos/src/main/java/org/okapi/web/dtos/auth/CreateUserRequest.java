/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.dtos.auth;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Builder
@ToString
public class CreateUserRequest {
  String firstName;
  String lastName;
  @NotNull String email;
  @NotNull String password;
}

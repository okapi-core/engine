/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.dtos.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Builder
public class UpdateUserRequest {
  String firstName;
  String lastName;
  String password;
  String oldPassword;
}

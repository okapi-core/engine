/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.dtos.org;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@AllArgsConstructor
@Getter
@Setter
@Builder
public class OrgMemberWDto {
  @NotNull String userId;
  String firstName;
  String lastName;
  @NotNull String email;
  boolean isAdmin;
}

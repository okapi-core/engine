/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.dtos.org;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.okapi.web.dtos.users.ORG_ROLE;

@AllArgsConstructor
@NoArgsConstructor
@Getter
public class UpdateOrgMemberRequest {
  @NotNull private String email;
  @NotNull private List<ORG_ROLE> roles;
}

/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.dtos.users;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@AllArgsConstructor
@Getter
@Builder
public class GetOrgUserView {
  @NotNull String orgId;
  @NotNull String orgName;
  @NotNull List<ORG_ROLE> roles;
}

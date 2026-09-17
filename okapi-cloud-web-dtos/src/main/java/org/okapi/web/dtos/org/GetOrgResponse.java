/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.dtos.org;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder(toBuilder = true)
public class GetOrgResponse {
  @NotNull private String orgId;
  @NotNull private String orgName;
  @NotNull private List<OrgMemberWDto> members;
}

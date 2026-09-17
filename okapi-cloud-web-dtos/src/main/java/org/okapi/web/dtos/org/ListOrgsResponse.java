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
import org.okapi.web.dtos.users.GetOrgUserView;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder(toBuilder = true)
public class ListOrgsResponse {
  @NotNull private List<GetOrgUserView> orgs;
}

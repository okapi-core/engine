/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.dtos.auth;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.okapi.web.dtos.org.GetOrgSummaryResponse;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
public class GetUserProfileResponse {
  @NotNull String id;
  String firstName;
  String lastName;
  @NotNull String email;
  @NotNull GetOrgSummaryResponse orgSummary;
}

/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.dtos.org;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class CreateOrgRequest {
  @NotNull private String orgName;
}

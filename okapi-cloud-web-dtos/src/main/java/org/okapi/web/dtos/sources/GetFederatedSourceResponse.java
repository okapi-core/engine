/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.dtos.sources;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
public class GetFederatedSourceResponse {
  @NotNull String sourceName;
  @NotNull String sourceType;
  Instant createdAt;
  @NotNull String registrationToken;
}

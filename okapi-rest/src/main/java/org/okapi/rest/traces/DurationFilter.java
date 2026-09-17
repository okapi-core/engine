/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.traces;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@ToString
public class DurationFilter {
  @JsonPropertyDescription("Minimum span duration in milliseconds (inclusive).")
  @NotNull
  long durMinMillis;

  @JsonPropertyDescription("Maximum span duration in milliseconds (inclusive).")
  @NotNull
  long durMaxMillis;
}

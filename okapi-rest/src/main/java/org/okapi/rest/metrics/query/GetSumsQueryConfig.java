/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.metrics.query;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
@ToString
public class GetSumsQueryConfig {
  public enum TEMPORALITY {
    DELTA_AGGREGATE,
    CUMULATIVE
  }

  @JsonPropertyDescription(
      "Sum temporality: DELTA_AGGREGATE (sum of deltas over the window) or CUMULATIVE (latest cumulative value).")
  TEMPORALITY temporality;
}

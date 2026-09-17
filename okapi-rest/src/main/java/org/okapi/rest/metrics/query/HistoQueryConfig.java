/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.metrics.query;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.*;

@AllArgsConstructor
@Getter
@Builder
@NoArgsConstructor
@ToString
public class HistoQueryConfig {
  public enum TEMPORALITY {
    CUMULATIVE,
    DELTA,
    MERGED
  }

  @JsonPropertyDescription(
"""
Histogram temporality: CUMULATIVE (running total), DELTA (per-window increments), or MERGED (delta buckets merged into a single distribution).
""")
  TEMPORALITY temporality;
}

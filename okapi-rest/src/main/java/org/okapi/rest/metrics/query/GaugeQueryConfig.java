/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.metrics.query;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.okapi.metrics.pojos.AGG_TYPE;
import org.okapi.metrics.pojos.RES_TYPE;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@ToString
public class GaugeQueryConfig {
  @JsonPropertyDescription(
"""
Time bucket size for grouping gauge samples: SECONDLY, MINUTELY, or HOURLY.
""")
  RES_TYPE resolution;

  @JsonPropertyDescription(
"""
Aggregation function applied within each time bucket (e.g. AVG, SUM, MAX).
 """)
  AGG_TYPE aggregation;
}

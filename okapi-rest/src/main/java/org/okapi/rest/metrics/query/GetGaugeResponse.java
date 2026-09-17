/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.metrics.query;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;
import lombok.*;
import org.okapi.metrics.pojos.AGG_TYPE;
import org.okapi.metrics.pojos.RES_TYPE;

@AllArgsConstructor
@Builder
@NoArgsConstructor
@Getter
@ToString
@JsonClassDescription("Gauge query results containing one or more time-series.")
public class GetGaugeResponse {
  @JsonPropertyDescription(
      "Time bucket resolution used to aggregate gauge samples (SECONDLY, MINUTELY, HOURLY).")
  RES_TYPE resolution;

  @JsonPropertyDescription("Aggregation function applied within each time bucket.")
  AGG_TYPE aggregation;

  @JsonPropertyDescription("List of gauge time-series, one per unique tag combination.")
  List<GaugeSeries> series;
}

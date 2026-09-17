/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.metrics.query;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@Getter
@Builder
@NoArgsConstructor
@JsonClassDescription("Histogram query results, grouped by tag set.")
public class GetHistogramResponse {
  @JsonPropertyDescription("List of histogram series, one per unique tag set.")
  List<HistogramSeries> series;
}

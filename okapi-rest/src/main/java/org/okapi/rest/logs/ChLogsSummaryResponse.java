/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.logs;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@AllArgsConstructor
@Builder
@Getter
@NoArgsConstructor
@ToString
@JsonClassDescription("Aggregate log analytics for a filtered time window.")
public class ChLogsSummaryResponse {
  long count;
  long bucketMillis;
  List<ChLogsTimelinePoint> volume;
  List<ChLogsSeverityTimelinePoint> severityTimeline;
  List<ChLogsSeverityCount> severityDistribution;
  List<ChLogsFacetCount> topServices;
  List<ChLogsFacetCount> topStreams;
  List<ChLogsServiceSeveritySummary> serviceSeverity;
}

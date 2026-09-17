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
public class LogsSummaryResponse {
  long count;
  long bucketMillis;
  List<LogsTimelinePoint> volume;
  List<LogsSeverityTimelinePoint> severityTimeline;
  List<LogsSeverityCount> severityDistribution;
  List<LogsFacetCount> topServices;
  List<LogsFacetCount> topStreams;
  List<LogsServiceSeveritySummary> serviceSeverity;
}

/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.engine.ch;

import java.util.List;
import org.okapi.rest.logs.*;

public class ChLogsApiMappers {

  public static LogsQueryResponseV2 toQueryResponse(ChLogsQueryResponse response) {
    return LogsQueryResponseV2.builder().items(response.getItems()).build();
  }

  public static LogsSummaryResponse toSummaryResponse(ChLogsSummaryResponse response) {
    return LogsSummaryResponse.builder()
        .count(response.getCount())
        .bucketMillis(response.getBucketMillis())
        .volume(toTimelinePoints(response.getVolume()))
        .severityTimeline(toSeverityTimelinePoints(response.getSeverityTimeline()))
        .severityDistribution(toSeverityCounts(response.getSeverityDistribution()))
        .topServices(toFacetCounts(response.getTopServices()))
        .topStreams(toFacetCounts(response.getTopStreams()))
        .serviceSeverity(toServiceSeveritySummaries(response.getServiceSeverity()))
        .build();
  }

  public static List<LogsTimelinePoint> toTimelinePoints(List<ChLogsTimelinePoint> points) {
    return points.stream().map(ChLogsApiMappers::toTimelinePoint).toList();
  }

  public static List<LogsSeverityTimelinePoint> toSeverityTimelinePoints(
      List<ChLogsSeverityTimelinePoint> points) {
    return points.stream().map(ChLogsApiMappers::toSeverityTimelinePoint).toList();
  }

  public static List<LogsSeverityCount> toSeverityCounts(List<ChLogsSeverityCount> counts) {
    return counts.stream().map(ChLogsApiMappers::toSeverityCount).toList();
  }

  public static List<LogsFacetCount> toFacetCounts(List<ChLogsFacetCount> counts) {
    return counts.stream().map(ChLogsApiMappers::toFacetCount).toList();
  }

  public static List<LogsServiceSeveritySummary> toServiceSeveritySummaries(
      List<ChLogsServiceSeveritySummary> summaries) {
    return summaries.stream().map(ChLogsApiMappers::toServiceSeveritySummary).toList();
  }

  public static LogsTimelinePoint toTimelinePoint(ChLogsTimelinePoint point) {
    return LogsTimelinePoint.builder()
        .bucketStartMs(point.getBucketStartMs())
        .count(point.getCount())
        .build();
  }

  public static LogsSeverityTimelinePoint toSeverityTimelinePoint(
      ChLogsSeverityTimelinePoint point) {
    return LogsSeverityTimelinePoint.builder()
        .bucketStartMs(point.getBucketStartMs())
        .logLevel(point.getLogLevel())
        .severity(point.getSeverity())
        .count(point.getCount())
        .build();
  }

  public static LogsSeverityCount toSeverityCount(ChLogsSeverityCount count) {
    return LogsSeverityCount.builder()
        .logLevel(count.getLogLevel())
        .severity(count.getSeverity())
        .count(count.getCount())
        .build();
  }

  public static LogsFacetCount toFacetCount(ChLogsFacetCount count) {
    return LogsFacetCount.builder().value(count.getValue()).count(count.getCount()).build();
  }

  public static LogsServiceSeveritySummary toServiceSeveritySummary(
      ChLogsServiceSeveritySummary summary) {
    return LogsServiceSeveritySummary.builder()
        .serviceName(summary.getServiceName())
        .total(summary.getTotal())
        .trace(summary.getTrace())
        .debug(summary.getDebug())
        .info(summary.getInfo())
        .warn(summary.getWarn())
        .error(summary.getError())
        .fatal(summary.getFatal())
        .errorRatio(summary.getErrorRatio())
        .build();
  }

  public static ChLogsQueryRequest toChQueryRequest(LogsQueryRequestV2 request) {
    if (request == null) {
      return null;
    }
    return ChLogsQueryRequest.builder()
        .tsStartNanos(request.getTsStartNanos())
        .tsEndNanos(request.getTsEndNanos())
        .limit(request.getLimit())
        .filters(request.getFilters())
        .includeAttributes(request.getIncludeAttributes())
        .includeResourceAttributes(request.getIncludeResourceAttributes())
        .attributeKeys(request.getAttributeKeys())
        .resourceAttributeKeys(request.getResourceAttributeKeys())
        .build();
  }

  public static ChLogsSummaryRequest toChSummaryRequest(LogsSummaryRequest request) {
    if (request == null) {
      return null;
    }
    return ChLogsSummaryRequest.builder()
        .tsStartNanos(request.getTsStartNanos())
        .tsEndNanos(request.getTsEndNanos())
        .filters(request.getFilters())
        .bucketMillis(request.getBucketMillis())
        .limit(request.getLimit())
        .build();
  }
}

/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.oscar.tools;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.okapi.ingester.client.IngesterClient;
import org.okapi.oscar.spring.ConfigKeys;
import org.okapi.oscar.tools.responses.FilterContribution;
import org.okapi.parallel.ParallelExecutor;
import org.okapi.rest.traces.SpanQueryV2Request;
import org.okapi.rest.traces.SpanQueryV2SummaryResponse;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class FilterContributionTool {

  IngesterClient client;
  ParallelExecutor executorService;

  public FilterContributionTool(
      IngesterClient client,
      @Value(ConfigKeys.CONTRIB_TOOL_THREAD_COUNT) int threadCount,
      @Value(ConfigKeys.CONTRIB_TOOL_THROTTLE) int throttle,
      @Value(ConfigKeys.CONTRIB_TOOL_DURATION_MILLIS) int durationMillis) {

    this.client = client;
    this.executorService =
        new ParallelExecutor(threadCount, throttle, Duration.ofMillis(durationMillis));
  }

  @Tool(
      description =
"""
Purpose
Return per-filter match counts for a span query to explain which filters are narrowing results.

When to use
1) A span query returns zero results and you need to identify which filter is too restrictive.
2) You want to compare filter selectivity to refine a span search.

Examples
1) Filter A (serviceFilter) returns 0 while filter B (traceId) returns 12.
2) Filter A (httpFilters) returns 3 while filter B (timestampFilter) returns 50.
Hint
Use this tool when a span query returns zero matches; retry without any filter that yields a count of 0.
""")
  public FilterContribution getFilterContributions(@ToolParam SpanQueryV2Request request) {
    var contribBuilder = FilterContribution.builder();
    var builderMethods =
        new ArrayList<Function<Long, FilterContribution.FilterContributionBuilder>>();
    var queries = new ArrayList<Supplier<SpanQueryV2SummaryResponse>>();

    var baseRequest = copyRequest(request);
    queries.add(() -> client.getResultsSummary(baseRequest));
    builderMethods.add(contribBuilder::overallCount);

    if (request.getTraceId() != null) {
      builderMethods.add(contribBuilder::traceIdRemovedCount);
      queries.add(() -> client.getResultsSummary(builderFrom(request).traceId(null).build()));
    }
    if (request.getSpanId() != null) {
      builderMethods.add(contribBuilder::spanIdRemovedCount);
      queries.add(() -> client.getResultsSummary(builderFrom(request).spanId(null).build()));
    }
    if (request.getKind() != null) {
      builderMethods.add(contribBuilder::kindRemovedCount);
      queries.add(() -> client.getResultsSummary(builderFrom(request).kind(null).build()));
    }
    if (request.getDbFilters() != null) {
      builderMethods.add(contribBuilder::dbFiltersRemovedCount);
      queries.add(() -> client.getResultsSummary(builderFrom(request).dbFilters(null).build()));
    }
    if (request.getDurationFilter() != null) {
      builderMethods.add(contribBuilder::durationFilterRemovedCount);
      queries.add(
          () -> client.getResultsSummary(builderFrom(request).durationFilter(null).build()));
    }
    if (request.getHttpFilters() != null) {
      builderMethods.add(contribBuilder::httpFiltersRemovedCount);
      queries.add(() -> client.getResultsSummary(builderFrom(request).httpFilters(null).build()));
    }
    if (request.getServiceFilter() != null) {
      builderMethods.add(contribBuilder::serviceFilterRemovedCount);
      queries.add(() -> client.getResultsSummary(builderFrom(request).serviceFilter(null).build()));
    }
    if (request.getTimestampFilter() != null) {
      builderMethods.add(contribBuilder::timestampFilterRemovedCount);
      queries.add(
          () -> client.getResultsSummary(builderFrom(request).timestampFilter(null).build()));
    }

    var stringAttributeCounts = new LinkedHashMap<String, Long>();
    if (request.getStringAttributesFilter() != null) {
      var filters = request.getStringAttributesFilter();
      for (int i = 0; i < filters.size(); i++) {
        var filter = filters.get(i);
        if (filter == null || filter.getKey() == null) {
          continue;
        }
        var trimmed = removeFilterAtIndex(filters, i);
        var updated =
            builderFrom(request).stringAttributesFilter(trimmed.isEmpty() ? null : trimmed).build();
        var summary = client.getResultsSummary(updated);
        stringAttributeCounts.put(filter.getKey(), summary.getCount());
      }
      if (!stringAttributeCounts.isEmpty()) {
        contribBuilder.stringAttributesRemovedCounts(stringAttributeCounts);
      }
    }

    var numberAttributeCounts = new java.util.LinkedHashMap<String, Long>();
    if (request.getNumberAttributesFilter() != null) {
      var filters = request.getNumberAttributesFilter();
      for (int i = 0; i < filters.size(); i++) {
        var filter = filters.get(i);
        if (filter == null || filter.getKey() == null) {
          continue;
        }
        var trimmed = removeFilterAtIndex(filters, i);
        var updated =
            builderFrom(request).numberAttributesFilter(trimmed.isEmpty() ? null : trimmed).build();
        var summary = client.getResultsSummary(updated);
        numberAttributeCounts.put(filter.getKey(), summary.getCount());
      }
      if (!numberAttributeCounts.isEmpty()) {
        contribBuilder.numberAttributesRemovedCounts(numberAttributeCounts);
      }
    }

    var results =
        executorService.submit(queries).stream().map(SpanQueryV2SummaryResponse::getCount).toList();
    for (int i = 0; i < results.size(); i++) {
      builderMethods.get(i).apply(results.get(i));
    }
    var contributions = contribBuilder.build();
    log.info("Got contributions: {}", contributions);
    return contributions;
  }

  private static SpanQueryV2Request copyRequest(SpanQueryV2Request request) {
    return builderFrom(request).build();
  }

  private static SpanQueryV2Request.SpanQueryV2RequestBuilder builderFrom(
      SpanQueryV2Request request) {
    return SpanQueryV2Request.builder()
        .traceId(request.getTraceId())
        .spanId(request.getSpanId())
        .kind(request.getKind())
        .dbFilters(request.getDbFilters())
        .durationFilter(request.getDurationFilter())
        .httpFilters(request.getHttpFilters())
        .serviceFilter(request.getServiceFilter())
        .timestampFilter(request.getTimestampFilter())
        .stringAttributesFilter(
            request.getStringAttributesFilter() == null
                ? null
                : new ArrayList<>(request.getStringAttributesFilter()))
        .numberAttributesFilter(
            request.getNumberAttributesFilter() == null
                ? null
                : new ArrayList<>(request.getNumberAttributesFilter()));
  }

  private static <T> List<T> removeFilterAtIndex(List<T> filters, int index) {
    var filtered = new ArrayList<>(filters);
    if (index >= 0 && index < filtered.size()) {
      filtered.remove(index);
    }
    filtered.removeIf(Objects::isNull);
    return filtered;
  }
}

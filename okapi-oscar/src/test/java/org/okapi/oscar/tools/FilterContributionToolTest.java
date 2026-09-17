/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.oscar.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;
import org.okapi.ingester.client.IngesterClient;
import org.okapi.oscar.tools.responses.FilterContribution;
import org.okapi.rest.traces.*;

class FilterContributionToolTest {

  static class FakeIngesterClient extends IngesterClient {
    private static final List<String> ALL_STRING_KEYS = List.of("db.system", "http.method");
    private static final List<String> ALL_NUMBER_KEYS = List.of("duration.ms", "http.status_code");
    private static final Map<String, Long> COUNTS =
        Map.ofEntries(
            Map.entry(
                signature(
                    true,
                    true,
                    true,
                    true,
                    true,
                    true,
                    true,
                    true,
                    ALL_STRING_KEYS,
                    ALL_NUMBER_KEYS),
                1000L),
            Map.entry(
                signature(
                    false,
                    true,
                    true,
                    true,
                    true,
                    true,
                    true,
                    true,
                    ALL_STRING_KEYS,
                    ALL_NUMBER_KEYS),
                1100L),
            Map.entry(
                signature(
                    true,
                    false,
                    true,
                    true,
                    true,
                    true,
                    true,
                    true,
                    ALL_STRING_KEYS,
                    ALL_NUMBER_KEYS),
                1200L),
            Map.entry(
                signature(
                    true,
                    true,
                    false,
                    true,
                    true,
                    true,
                    true,
                    true,
                    ALL_STRING_KEYS,
                    ALL_NUMBER_KEYS),
                1300L),
            Map.entry(
                signature(
                    true,
                    true,
                    true,
                    false,
                    true,
                    true,
                    true,
                    true,
                    ALL_STRING_KEYS,
                    ALL_NUMBER_KEYS),
                1400L),
            Map.entry(
                signature(
                    true,
                    true,
                    true,
                    true,
                    false,
                    true,
                    true,
                    true,
                    ALL_STRING_KEYS,
                    ALL_NUMBER_KEYS),
                1500L),
            Map.entry(
                signature(
                    true,
                    true,
                    true,
                    true,
                    true,
                    false,
                    true,
                    true,
                    ALL_STRING_KEYS,
                    ALL_NUMBER_KEYS),
                1600L),
            Map.entry(
                signature(
                    true,
                    true,
                    true,
                    true,
                    true,
                    true,
                    false,
                    true,
                    ALL_STRING_KEYS,
                    ALL_NUMBER_KEYS),
                1700L),
            Map.entry(
                signature(
                    true,
                    true,
                    true,
                    true,
                    true,
                    true,
                    true,
                    false,
                    ALL_STRING_KEYS,
                    ALL_NUMBER_KEYS),
                1800L),
            Map.entry(
                signature(
                    true,
                    true,
                    true,
                    true,
                    true,
                    true,
                    true,
                    true,
                    List.of("db.system"),
                    ALL_NUMBER_KEYS),
                2100L),
            Map.entry(
                signature(
                    true,
                    true,
                    true,
                    true,
                    true,
                    true,
                    true,
                    true,
                    List.of("http.method"),
                    ALL_NUMBER_KEYS),
                2200L),
            Map.entry(
                signature(
                    true,
                    true,
                    true,
                    true,
                    true,
                    true,
                    true,
                    true,
                    ALL_STRING_KEYS,
                    List.of("duration.ms")),
                3100L),
            Map.entry(
                signature(
                    true,
                    true,
                    true,
                    true,
                    true,
                    true,
                    true,
                    true,
                    ALL_STRING_KEYS,
                    List.of("http.status_code")),
                3200L),
            Map.entry(
                signature(
                    true,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    List.of("http.method"),
                    List.of()),
                5000L),
            Map.entry(
                signature(
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    List.of("http.method"),
                    List.of()),
                5100L),
            Map.entry(
                signature(
                    true, false, false, false, false, false, false, false, List.of(), List.of()),
                5200L));

    FakeIngesterClient() {
      super("http://localhost", new OkHttpClient(), null);
    }

    @Override
    public SpanQueryV2SummaryResponse getResultsSummary(SpanQueryV2Request request) {
      long count = resolveCount(request);
      return SpanQueryV2SummaryResponse.builder().count(count).build();
    }

    private long resolveCount(SpanQueryV2Request request) {
      return COUNTS.getOrDefault(signature(request), 0L);
    }

    private static String signature(
        boolean hasTraceId,
        boolean hasSpanId,
        boolean hasKind,
        boolean hasDbFilters,
        boolean hasDurationFilter,
        boolean hasHttpFilters,
        boolean hasServiceFilter,
        boolean hasTimestampFilter,
        List<String> stringKeys,
        List<String> numberKeys) {
      return String.join(
          "|",
          "trace=" + (hasTraceId ? "1" : "0"),
          "span=" + (hasSpanId ? "1" : "0"),
          "kind=" + (hasKind ? "1" : "0"),
          "db=" + (hasDbFilters ? "1" : "0"),
          "dur=" + (hasDurationFilter ? "1" : "0"),
          "http=" + (hasHttpFilters ? "1" : "0"),
          "svc=" + (hasServiceFilter ? "1" : "0"),
          "ts=" + (hasTimestampFilter ? "1" : "0"),
          "str=" + String.join(",", stringKeys),
          "num=" + String.join(",", numberKeys));
    }

    private static String signature(SpanQueryV2Request request) {
      return signature(
          request.getTraceId() != null,
          request.getSpanId() != null,
          request.getKind() != null,
          request.getDbFilters() != null,
          request.getDurationFilter() != null,
          request.getHttpFilters() != null,
          request.getServiceFilter() != null,
          request.getTimestampFilter() != null,
          extractStringKeys(request.getStringAttributesFilter()),
          extractNumberKeys(request.getNumberAttributesFilter()));
    }

    private static List<String> extractStringKeys(List<StringAttributeFilter> filters) {
      if (filters == null || filters.isEmpty()) {
        return List.of();
      }
      return filters.stream()
          .filter(filter -> filter != null && filter.getKey() != null)
          .map(StringAttributeFilter::getKey)
          .sorted()
          .toList();
    }

    private static List<String> extractNumberKeys(List<NumberAttributeFilter> filters) {
      if (filters == null || filters.isEmpty()) {
        return List.of();
      }
      return filters.stream()
          .filter(filter -> filter != null && filter.getKey() != null)
          .map(NumberAttributeFilter::getKey)
          .sorted()
          .toList();
    }
  }

  @Test
  void populatesCountsForAllFilters() {
    var client = new FakeIngesterClient();
    var tool = new FilterContributionTool(client, 4, 4, 1000);
    var request =
        SpanQueryV2Request.builder()
            .traceId("trace-1")
            .spanId("span-1")
            .kind("SPAN_KIND_SERVER")
            .dbFilters(DbFilters.builder().system("postgresql").build())
            .durationFilter(DurationFilter.builder().durMinMillis(10).durMaxMillis(20).build())
            .httpFilters(HttpFilters.builder().httpMethod("GET").statusCode(200).build())
            .serviceFilter(ServiceFilter.builder().service("svc-a").peer("svc-b").build())
            .timestampFilter(TimestampFilter.builder().tsStartNanos(1L).tsEndNanos(2L).build())
            .stringAttributesFilter(
                List.of(
                    StringAttributeFilter.builder().key("http.method").value("GET").build(),
                    StringAttributeFilter.builder().key("db.system").value("postgresql").build()))
            .numberAttributesFilter(
                List.of(
                    NumberAttributeFilter.builder().key("http.status_code").value(200.0).build(),
                    NumberAttributeFilter.builder().key("duration.ms").value(123.0).build()))
            .build();

    FilterContribution result = tool.getFilterContributions(request);

    assertEquals(1000L, result.getOverallCount());
    assertEquals(1100L, result.getTraceIdRemovedCount());
    assertEquals(1200L, result.getSpanIdRemovedCount());
    assertEquals(1300L, result.getKindRemovedCount());
    assertEquals(1400L, result.getDbFiltersRemovedCount());
    assertEquals(1500L, result.getDurationFilterRemovedCount());
    assertEquals(1600L, result.getHttpFiltersRemovedCount());
    assertEquals(1700L, result.getServiceFilterRemovedCount());
    assertEquals(1800L, result.getTimestampFilterRemovedCount());
    assertEquals(
        Map.of("http.method", 2100L, "db.system", 2200L),
        result.getStringAttributesRemovedCounts());
    assertEquals(
        Map.of("http.status_code", 3100L, "duration.ms", 3200L),
        result.getNumberAttributesRemovedCounts());
  }

  @Test
  void leavesCountsNullWhenFiltersAreMissing() {
    var client = new FakeIngesterClient();
    var tool = new FilterContributionTool(client, 2, 2, 1000);
    var request =
        SpanQueryV2Request.builder()
            .traceId("trace-1")
            .stringAttributesFilter(
                List.of(StringAttributeFilter.builder().key("http.method").value("GET").build()))
            .build();

    FilterContribution result = tool.getFilterContributions(request);

    assertEquals(5000L, result.getOverallCount());
    assertEquals(5100L, result.getTraceIdRemovedCount());
    assertNull(result.getSpanIdRemovedCount());
    assertNull(result.getKindRemovedCount());
    assertNull(result.getDbFiltersRemovedCount());
    assertNull(result.getDurationFilterRemovedCount());
    assertNull(result.getHttpFiltersRemovedCount());
    assertNull(result.getServiceFilterRemovedCount());
    assertNull(result.getTimestampFilterRemovedCount());
    assertEquals(Map.of("http.method", 5200L), result.getStringAttributesRemovedCounts());
    assertNull(result.getNumberAttributesRemovedCounts());
  }
}

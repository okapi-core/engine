/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.oscar.tools;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.okapi.metrics.pojos.AGG_TYPE;
import org.okapi.metrics.pojos.RES_TYPE;
import org.okapi.rest.logs.ChLogFilter;
import org.okapi.rest.logs.ChLogRow;
import org.okapi.rest.logs.ChLogsQueryRequest;
import org.okapi.rest.logs.ChLogsQueryResponse;
import org.okapi.rest.metrics.query.*;
import org.okapi.rest.search.MetricPath;
import org.okapi.rest.search.SearchMetricsV2Response;
import org.okapi.rest.traces.*;

public class ToolCallSummariesTest {

  @Test
  void summarizeLogSearchRequestIncludesWindowFiltersAndLimit() {
    var request =
        ChLogsQueryRequest.builder()
            .tsStartNanos(1L)
            .tsEndNanos(2L)
            .limit(10)
            .filters(List.of(ChLogFilter.builder().build()))
            .build();

    assertThat(ToolCallSummaries.summarizeLogSearchRequest(request))
        .isEqualTo("Searching logs: timeNs=[1,2] filters=1 limit=10");
  }

  @Test
  void summarizeLogSearchResponseReportsCount() {
    var response =
        ChLogsQueryResponse.builder()
            .items(List.of(ChLogRow.builder().body("failed").build()))
            .build();

    assertThat(ToolCallSummaries.summarizeLogSearchResponse(response))
        .isEqualTo("Search logs results: logs=1");
  }

  @Test
  void summarizeSpanQueryRequestIncludesKeyFilters() {
    var request =
        SpanQueryV2Request.builder()
            .traceId("trace-1")
            .spanId("span-1")
            .kind("SPAN_KIND_SERVER")
            .serviceFilter(ServiceFilter.builder().service("checkout").peer("db").build())
            .timestampFilter(TimestampFilter.builder().tsStartNanos(1L).tsEndNanos(2L).build())
            .httpFilters(HttpFilters.builder().httpMethod("GET").statusCode(200).build())
            .dbFilters(DbFilters.builder().system("postgres").operation("select").build())
            .stringAttributesFilter(
                List.of(StringAttributeFilter.builder().key("env").value("prod").build()))
            .numberAttributesFilter(
                List.of(NumberAttributeFilter.builder().key("latency").value(2.0).build()))
            .build();

    String summary = ToolCallSummaries.summarizeSpanQueryRequest(request);

    assertThat(summary)
        .contains("Fetching spans:")
        .contains("traceId=trace-1")
        .contains("spanId=span-1")
        .contains("kind=SPAN_KIND_SERVER")
        .contains("service=checkout")
        .contains("peer=db")
        .contains("timeNs=[1,2]")
        .contains("httpFilters=2")
        .contains("dbFilters=2")
        .contains("stringAttrs=1")
        .contains("numberAttrs=1");
  }

  @Test
  void summarizeSpanQueryResponseReportsCount() {
    var response =
        SpanQueryV2Response.builder()
            .items(List.of(SpanRowV2.builder().traceId("trace").spanId("span").build()))
            .build();

    String summary = ToolCallSummaries.summarizeSpanQueryResponse(response);

    assertThat(summary).isEqualTo("Fetch span results: spans=1");
  }

  @Test
  void summarizeGetMetricsResponseGaugeReportsPointsAndWindow() {
    var request =
        GetMetricsRequest.builder()
            .metric("cpu.usage")
            .tags(Map.of("host", "web-1"))
            .start(1L)
            .end(3L)
            .metricType(METRIC_TYPE.GAUGE)
            .gaugeQueryConfig(new GaugeQueryConfig(RES_TYPE.MINUTELY, AGG_TYPE.AVG))
            .build();
    var response =
        GetMetricsResponse.builder()
            .metric("cpu.usage")
            .tags(Map.of("host", "web-1"))
            .gaugeResponse(
                org.okapi.rest.metrics.query.GetGaugeResponse.builder()
                    .series(
                        List.of(
                            GaugeSeries.builder()
                                .tags(Map.of("host", "web-1"))
                                .times(List.of(1L, 2L, 3L))
                                .values(List.of(1.0f, 2.0f, 3.0f))
                                .build()))
                    .build())
            .build();

    String summary = ToolCallSummaries.summarizeGetMetricsResponse(request, response);

    assertThat(summary)
        .contains("points=3")
        .contains("results=1")
        .contains("time-ms=[1,3]")
        .contains("metric=cpu.usage{host=web-1}")
        .contains("temporality=n/a");
  }

  @Test
  void summarizeGetMetricsResponseHistogramReportsTemporality() {
    var request =
        GetMetricsRequest.builder()
            .metric("http.server.duration")
            .tags(Map.of("route", "/"))
            .start(10L)
            .end(40L)
            .metricType(METRIC_TYPE.HISTO)
            .histoQueryConfig(
                HistoQueryConfig.builder().temporality(HistoQueryConfig.TEMPORALITY.DELTA).build())
            .build();
    var response =
        GetMetricsResponse.builder()
            .metric("http.server.duration")
            .tags(Map.of("route", "/"))
            .histogramResponse(
                GetHistogramResponse.builder()
                    .series(
                        List.of(
                            HistogramSeries.builder()
                                .histogram(
                                    Histogram.builder()
                                        .buckets(List.of(0.1f, 0.2f))
                                        .counts(List.of(1L, 2L, 3L))
                                        .start(10L)
                                        .end(20L)
                                        .build())
                                .build(),
                            HistogramSeries.builder()
                                .histogram(Histogram.builder().start(30L).end(40L).build())
                                .build()))
                    .build())
            .build();

    String summary = ToolCallSummaries.summarizeGetMetricsResponse(request, response);

    assertThat(summary)
        .isEqualTo(
            "Fetch metrics results:  temporality=DELTA time-ms=[10,40] metric=http.server.duration{route=/} results=2 histos=[buckets=[0.1, 0.2], counts=[0.1, 0.2]] malformed-histogram");
  }

  @Test
  void summarizeSearchMetricsResponseReportsCount() {
    var response =
        SearchMetricsV2Response.builder()
            .matchingPaths(List.of(MetricPath.builder().metric("cpu.usage").build()))
            .build();

    String summary = ToolCallSummaries.summarizeSearchMetricsResponse(response);

    assertThat(summary).isEqualTo("Search metrics results: matches=1");
  }
}

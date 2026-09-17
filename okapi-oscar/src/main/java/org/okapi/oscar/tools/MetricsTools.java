/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.oscar.tools;

import lombok.extern.slf4j.Slf4j;
import org.okapi.ingester.client.IngesterClient;
import org.okapi.rest.metrics.query.GetMetricsRequest;
import org.okapi.rest.metrics.query.GetMetricsResponse;
import org.okapi.rest.search.AnyMetricOrValueFilter;
import org.okapi.rest.search.SearchMetricsRequest;
import org.okapi.rest.search.SearchMetricsV2Response;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

@Slf4j
public class MetricsTools {
  private final IngesterClient client;
  private final ToolCallReporter toolCallReporter;

  public MetricsTools(IngesterClient client, ToolCallReporter toolCallReporter) {
    this.client = client;
    this.toolCallReporter = toolCallReporter;
  }

  @Tool(
      description =
"""
Search for metric paths (name + tag combinations) emitted in a time window to discover available
metrics, tag sets, or whether a metric exists at all.
tsStartMillis and tsEndMillis MUST be in MILLISECONDS since Unix epoch.
""")
  public SearchMetricsV2Response searchMetrics(@ToolParam SearchMetricsRequest request) {
    log.debug("searchMetrics req: {}", request);
    toolCallReporter.reportRequest(
        "searchMetrics", request, ToolCallSummaries.summarizeSearchMetricsRequest(request));
    var response = client.searchMetrics(request);
    toolCallReporter.reportResponse(
        "searchMetrics", ToolCallSummaries.summarizeSearchMetricsResponse(response));
    log.debug("searchMetrics res: {}", response);
    return response;
  }

  @Tool(
      description =
"""
Discover metric paths for a specific host. Multiple metrics related to the specific host will be returned.
""")
  public SearchMetricsV2Response discoverMetricsForHost(
      @ToolParam(
              description =
"""
Name of the host for which to search metrics. Host value will match exactly.
RE2 patterns should not be used here.
""")
          String host) {
    log.debug("Discovery metrics: {}", host);
    var response = discoverMetricsFor("discoverMetricsForHost", host);
    log.debug("Discovered metrics: {}", response);
    return response;
  }

  @Tool(
      description =
"""
Discover metric paths for hosts matching a pattern.
""")
  public SearchMetricsV2Response discoverMetricsForHostPattern(
      @ToolParam(description = "RE2 pattern to match host values.") String pattern) {
    return discoverMetricsForPattern("discoverMetricsForHostPattern", pattern);
  }

  @Tool(
      description =
"""
Discover metric paths for a specific database value. Note - This uses an exact value match across metric names and tags.
""")
  public SearchMetricsV2Response discoverMetricsForDb(
      @ToolParam(description = "Database value to match exactly.") String db) {
    return discoverMetricsFor("discoverMetricsForDb", db);
  }

  @Tool(
      description =
"""
Discover metric paths for database values matching a pattern.
e.g. This uses an RE2 pattern match across metric names and tags.
""")
  public SearchMetricsV2Response discoverMetricsForDbPattern(
      @ToolParam(description = "RE2 pattern to match database values.") String dbNamePattern) {
    return discoverMetricsForPattern("discoverMetricsForDbPattern", dbNamePattern);
  }

  @Tool(
      description =
"""
Get metrics data. This method can return histograms, gauges and counters (or sums).
Gauges have a specific resolution and gaugeQueryConfig should be set when a doing queries for a gauge metric.
Histograms have a specific temporality either DELTA, CUMULATIVE or MERGED. When histograms are queried, histogramQueryConfig should be specified.
Sums also have a specific temporality also either DELTA or CUMULATIVE. When sums are queried, sumsQueryConfig should be specified.
When querying histograms or sums temporality should be specified.

""")
  public GetMetricsResponse getMetrics(@ToolParam GetMetricsRequest request) {
    log.debug("req: {}", request);
    toolCallReporter.reportRequest(
        "getMetrics", request, ToolCallSummaries.summarizeGetMetricsRequest(request));
    var response = client.query(request);
    toolCallReporter.reportResponse(
        "getMetrics", ToolCallSummaries.summarizeGetMetricsResponse(request, response));
    log.debug("res: {}", response);
    return response;
  }

  private SearchMetricsV2Response discoverMetricsFor(String toolName, String value) {
    var request =
        SearchMetricsRequest.builder()
            .tsStartMillis(0)
            .tsEndMillis(System.currentTimeMillis())
            .anyMetricOrValueFilter(AnyMetricOrValueFilter.builder().value(value).build())
            .build();
    toolCallReporter.reportRequest(
        toolName, request, ToolCallSummaries.summarizeSearchMetricsRequest(request));
    var response = client.searchMetrics(request);
    toolCallReporter.reportResponse(
        toolName, ToolCallSummaries.summarizeSearchMetricsResponse(response));
    return response;
  }

  private SearchMetricsV2Response discoverMetricsForPattern(String toolName, String pattern) {
    var request =
        SearchMetricsRequest.builder()
            .tsStartMillis(0)
            .tsEndMillis(System.currentTimeMillis())
            .anyMetricOrValueFilter(AnyMetricOrValueFilter.builder().pattern(pattern).build())
            .build();
    toolCallReporter.reportRequest(
        toolName, request, ToolCallSummaries.summarizeSearchMetricsRequest(request));
    var response = client.searchMetrics(request);
    toolCallReporter.reportResponse(
        toolName, ToolCallSummaries.summarizeSearchMetricsResponse(response));
    return response;
  }
}

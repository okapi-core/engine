/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.oscar.tools;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.okapi.ingester.client.IngesterClient;
import org.okapi.metrics.pojos.RES_TYPE;
import org.okapi.rest.traces.SpanQueryV2Request;
import org.okapi.rest.traces.SpanQueryV2Response;
import org.okapi.rest.traces.TimestampFilter;
import org.okapi.rest.traces.red.ServiceRedRequest;
import org.okapi.rest.traces.red.ServiceRedResponse;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

@Slf4j
public class TracingTools {

  private final IngesterClient client;
  private final ToolCallReporter toolCallReporter;

  public TracingTools(IngesterClient client, ToolCallReporter toolCallReporter) {
    this.client = client;
    this.toolCallReporter = toolCallReporter;
  }

  @Tool(
      description =
"""
Use this tool to search for spans. When in doubt set the minimum set of filters necessary.
Timestampfilter is always required without which the call will fail. To set timestamp filter, use the following guidelines:
1. If the user has specified a time-interval for the investigation use that time interval.
2. Usually a time interval of 1 hr is enough. You can use timeRange tool with the appropriate duration to get a time range that can be used to populate this filter.
""")
  public SpanQueryV2Response getSpans(@ToolParam SpanQueryV2Request request) {
    log.debug("Calling {}", request);
    toolCallReporter.reportRequest(
        "getSpans", request, ToolCallSummaries.summarizeSpanQueryRequest(request));
    var response = client.querySpans(request);
    toolCallReporter.reportResponseSummaryOnly(
        "getSpans", ToolCallSummaries.summarizeSpanQueryResponse(response));
    log.debug("Response: {}", response);
    return response;
  }

  @Tool(
      description =
"""
Get RED metrics (Rate, Error rate, Duration) for a service. Returns aggregate RED metrics for the service, per-operation breakdowns, and per-peer-edge breakdowns.
""")
  public ServiceRedResponse getServiceRedMetrics(@ToolParam ServiceRedRequest request) {
    toolCallReporter.reportRequest(
        "getServiceRedMetrics", request, "Fetching service RED metrics.");
    var response = client.getServiceReds(request);
    toolCallReporter.reportResponse("getServiceRedMetrics", "Fetch service RED metrics results.");
    return response;
  }

  @Tool(
      description =
"""
Discover downstream peer services for a given service over a time window.
This tool is used to get dependencies of a specific service.
If no dependencies are sent, it might be a missing instrumentation problem. Other signals should be consulted.
""")
  public List<String> discoverPeers(
      @ToolParam(description = "Service name to inspect.") String service,
      @ToolParam(description = "Start timestamp in nanoseconds since Unix epoch.") long startNs,
      @ToolParam(description = "End timestamp in nanoseconds since Unix epoch.") long endNs) {
    toolCallReporter.reportRequest(
        "discoverPeers",
        Map.of("service", service, "startNs", startNs, "endNs", endNs),
        ToolCallSummaries.summarizeDiscoverPeersRequest(service, startNs, endNs));
    var response = getServiceReds(service, startNs, endNs);
    if (response.getPeerReds() == null) {
      var empty = Collections.<String>emptyList();
      toolCallReporter.reportResponse(
          "discoverPeers", ToolCallSummaries.summarizeDiscoverPeersResponse(empty));
      return empty;
    }
    var peers =
        response.getPeerReds().stream()
            .map(peer -> peer == null ? null : peer.getPeerService())
            .filter(name -> name != null && !name.isBlank())
            .toList();
    toolCallReporter.reportResponse(
        "discoverPeers", ToolCallSummaries.summarizeDiscoverPeersResponse(peers));
    return peers;
  }

  private ServiceRedResponse getServiceReds(String service, long startNs, long endNs) {
    var request =
        ServiceRedRequest.builder()
            .service(service)
            .timestampFilter(
                TimestampFilter.builder().tsStartNanos(startNs).tsEndNanos(endNs).build())
            .resType(RES_TYPE.MINUTELY)
            .build();
    return client.getServiceReds(request);
  }
}

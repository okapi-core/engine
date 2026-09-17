/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.metrics.api;

import lombok.RequiredArgsConstructor;
import org.okapi.engine.api.MetricsEngine;
import org.okapi.rest.metrics.exemplar.GetExemplarsRequest;
import org.okapi.rest.metrics.exemplar.GetExemplarsResponse;
import org.okapi.rest.metrics.query.GetMetricsRequest;
import org.okapi.rest.metrics.query.GetMetricsResponse;
import org.okapi.rest.search.GetMetricNameHints;
import org.okapi.rest.search.GetMetricsHintsResponse;
import org.okapi.rest.search.GetTagHintsRequest;
import org.okapi.rest.search.GetTagValueHintsRequest;
import org.okapi.rest.search.SearchMetricsRequest;
import org.okapi.rest.search.SearchMetricsV2Response;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class MetricsV2Controller {
  private final MetricsEngine metricsEngine;

  @PostMapping("/metrics/query")
  public GetMetricsResponse getMetricsResponse(
      @RequestBody @Validated GetMetricsRequest getMetricsRequest) throws Exception {
    return metricsEngine.query(getMetricsRequest);
  }

  @PostMapping("/metrics/name/hints")
  public GetMetricsHintsResponse getMetricSuggestions(@RequestBody GetMetricNameHints request)
      throws Exception {
    return metricsEngine.metricHints(request);
  }

  @PostMapping("/metrics/tag/hints")
  public GetMetricsHintsResponse getTagHints(@RequestBody GetTagHintsRequest request)
      throws Exception {
    return metricsEngine.tagHints(request);
  }

  @PostMapping("/metrics/tag-value/hints")
  public GetMetricsHintsResponse getTagValueHints(@RequestBody GetTagValueHintsRequest request)
      throws Exception {
    return metricsEngine.tagValueHints(request);
  }

  @PostMapping("/metrics/exemplars")
  public GetExemplarsResponse getExemplars(@RequestBody GetExemplarsRequest request)
      throws Exception {
    return metricsEngine.exemplars(request);
  }

  @PostMapping("/metrics/search")
  public SearchMetricsV2Response searchMetrics(@RequestBody SearchMetricsRequest request)
      throws Exception {
    return metricsEngine.search(request);
  }
}

/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.controller;

import lombok.AllArgsConstructor;
import org.okapi.exceptions.MalformedQueryException;
import org.okapi.rest.metrics.exemplar.GetExemplarsBatchResponse;
import org.okapi.rest.metrics.exemplar.GetExemplarsRequest;
import org.okapi.rest.metrics.exemplar.GetExemplarsResponse;
import org.okapi.rest.metrics.query.GetMetricsBatchResponse;
import org.okapi.rest.metrics.query.GetMetricsRequest;
import org.okapi.rest.metrics.query.GetMetricsResponse;
import org.okapi.rest.search.GetMetricNameHints;
import org.okapi.rest.search.GetMetricsHintsResponse;
import org.okapi.rest.search.GetTagHintsRequest;
import org.okapi.rest.search.GetTagValueHintsRequest;
import org.okapi.web.dtos.dashboards.MultiQueryRequest;
import org.okapi.web.service.query.MetricsQueryService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/metrics")
@AllArgsConstructor
public class MetricsQueryController {

  MetricsQueryService metricsQueryService;

  @PostMapping("/query")
  public GetMetricsResponse getMetricsResponse(@RequestBody GetMetricsRequest request) {
    return metricsQueryService.queryMetrics(request);
  }

  @PostMapping("/query/batch")
  public GetMetricsBatchResponse getMetricsBatchResponse(
      @RequestBody MultiQueryRequest multiQueryRequest) throws MalformedQueryException {
    return metricsQueryService.queryMetrics(multiQueryRequest);
  }

  @PostMapping("/name/hints")
  public GetMetricsHintsResponse getMetricHints(@RequestBody GetMetricNameHints request) {
    return metricsQueryService.getMetricHints(request);
  }

  @PostMapping("/tag/hints")
  public GetMetricsHintsResponse getTagHints(@RequestBody GetTagHintsRequest request) {
    return metricsQueryService.getTagHints(request);
  }

  @PostMapping("/tag-value/hints")
  public GetMetricsHintsResponse getTagValueHints(@RequestBody GetTagValueHintsRequest request) {
    return metricsQueryService.getTagValueHints(request);
  }

  @PostMapping("/metrics/exemplars")
  public GetExemplarsResponse getMetricExemplars(@RequestBody GetExemplarsRequest request) {
    return metricsQueryService.getExemplarsResponse(request);
  }

  @PostMapping("/exemplars/query")
  public GetExemplarsBatchResponse queryMetricExemplars(
      @RequestBody MultiQueryRequest multiQueryRequest) throws MalformedQueryException {
    return metricsQueryService.queryExemplars(multiQueryRequest);
  }
}

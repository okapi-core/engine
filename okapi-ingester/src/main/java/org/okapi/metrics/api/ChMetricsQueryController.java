/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.metrics.api;

import lombok.RequiredArgsConstructor;
import org.okapi.metrics.ch.ChMetricsQueryProcessor;
import org.okapi.metrics.ch.ChSearchMetricsProcessor;
import org.okapi.rest.metrics.exemplar.GetExemplarsRequest;
import org.okapi.rest.metrics.exemplar.GetExemplarsResponse;
import org.okapi.rest.metrics.query.GetMetricsRequest;
import org.okapi.rest.metrics.query.GetMetricsResponse;
import org.okapi.rest.search.*;
import org.okapi.rest.search.SearchMetricsV2Response;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class ChMetricsQueryController {
  private final ChMetricsQueryProcessor chMetricsQueryProcessor;
  private final ChSearchMetricsProcessor searchMetricsProcessor;

  @PostMapping("/metrics/query")
  public GetMetricsResponse getMetricsResponse(
      @RequestBody @Validated GetMetricsRequest getMetricsRequest) {
    return chMetricsQueryProcessor.getMetricsResponse(getMetricsRequest);
  }

  @PostMapping("/metrics/name/hints")
  public GetMetricsHintsResponse getMetricSuggestions(
      @RequestBody GetMetricNameHints hintsRequest) {
    return chMetricsQueryProcessor.getMetricHints(hintsRequest);
  }

  @PostMapping("/metrics/tag/hints")
  public GetMetricsHintsResponse getTagHints(@RequestBody GetTagHintsRequest request) {
    return chMetricsQueryProcessor.getTagHints(request);
  }

  @PostMapping("/metrics/tag-value/hints")
  public GetMetricsHintsResponse getTagValueHints(@RequestBody GetTagValueHintsRequest request) {
    return chMetricsQueryProcessor.getTagValueHints(request);
  }

  @PostMapping("/metrics/exemplars")
  public GetExemplarsResponse getExemplars(@RequestBody GetExemplarsRequest request) {
    return chMetricsQueryProcessor.getExemplars(request);
  }

  @PostMapping("/metrics/search")
  public SearchMetricsV2Response searchMetrics(@RequestBody SearchMetricsRequest request) {
    return searchMetricsProcessor.searchMetricsResponse(request);
  }
}

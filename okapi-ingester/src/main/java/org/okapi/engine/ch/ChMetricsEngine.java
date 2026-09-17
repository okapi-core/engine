/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.engine.ch;

import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import lombok.RequiredArgsConstructor;
import org.okapi.engine.api.MetricsEngine;
import org.okapi.metrics.ch.ChMetricsIngester;
import org.okapi.metrics.ch.ChMetricsQueryProcessor;
import org.okapi.metrics.ch.ChSearchMetricsProcessor;
import org.okapi.metrics.otel.ConversionConfig;
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
import org.okapi.spring.configs.Profiles;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile(Profiles.PROFILE_CH)
@RequiredArgsConstructor
public class ChMetricsEngine implements MetricsEngine {
  private final ChMetricsQueryProcessor queryProcessor;
  private final ChSearchMetricsProcessor searchProcessor;
  private final ChMetricsIngester ingester;

  @Override
  public GetMetricsResponse query(GetMetricsRequest request) {
    return queryProcessor.getMetricsResponse(request);
  }

  @Override
  public GetMetricsHintsResponse metricHints(GetMetricNameHints request) {
    return queryProcessor.getMetricHints(request);
  }

  @Override
  public GetMetricsHintsResponse tagHints(GetTagHintsRequest request) {
    return queryProcessor.getTagHints(request);
  }

  @Override
  public GetMetricsHintsResponse tagValueHints(GetTagValueHintsRequest request) {
    return queryProcessor.getTagValueHints(request);
  }

  @Override
  public GetExemplarsResponse exemplars(GetExemplarsRequest request) {
    return queryProcessor.getExemplars(request);
  }

  @Override
  public SearchMetricsV2Response search(SearchMetricsRequest request) {
    return searchProcessor.searchMetricsResponse(request);
  }

  @Override
  public void ingest(ExportMetricsServiceRequest request, ConversionConfig conversionConfig)
      throws Exception {
    ingester.ingestOtelProtobuf(request, conversionConfig);
  }
}

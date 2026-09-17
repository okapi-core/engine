/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.engine.api;

import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
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

public interface MetricsEngine {
  GetMetricsResponse query(GetMetricsRequest request) throws Exception;

  GetMetricsHintsResponse metricHints(GetMetricNameHints request) throws Exception;

  GetMetricsHintsResponse tagHints(GetTagHintsRequest request) throws Exception;

  GetMetricsHintsResponse tagValueHints(GetTagValueHintsRequest request) throws Exception;

  GetExemplarsResponse exemplars(GetExemplarsRequest request) throws Exception;

  SearchMetricsV2Response search(SearchMetricsRequest request) throws Exception;

  void ingest(ExportMetricsServiceRequest request, ConversionConfig conversionConfig)
      throws Exception;
}

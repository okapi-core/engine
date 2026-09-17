/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.service.query;

import com.google.gson.Gson;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.okapi.exceptions.BadRequestException;
import org.okapi.exceptions.MalformedQueryException;
import org.okapi.grammar.GRAMMAR;
import org.okapi.ingester.client.IngesterClient;
import org.okapi.ingester.client.ProxyResponseTranslator;
import org.okapi.parallel.ParallelExecutor;
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
import org.okapi.rest.traces.TimestampFilter;
import org.okapi.web.dtos.dashboards.MultiQueryRequest;
import org.okapi.web.service.Configs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class MetricsQueryService {

  OkHttpClient client;
  ProxyResponseTranslator translator;
  String clusterEndpoint;
  ParallelExecutor parallelExecutor;
  long timeoutMillis;
  Gson gson = new Gson();
  IngesterClient ingesterClient;

  public MetricsQueryService(
      OkHttpClient client,
      ProxyResponseTranslator proxyResponseTranslator,
      @Value(Configs.CLUSTER_EP) String clusterEndpoint,
      @Value(Configs.CONCURRENT_QUERY_THREADS) int queryThreads,
      @Value(Configs.CONCURRENCY_QUERY_LIM) int queryLim,
      @Value(Configs.QUERY_TIMEOUT) long timeoutMillis,
      IngesterClient ingesterClient) {
    this.client = client;
    this.translator = proxyResponseTranslator;
    this.clusterEndpoint = clusterEndpoint;
    this.parallelExecutor = new ParallelExecutor(queryLim, queryThreads);
    this.timeoutMillis = timeoutMillis;
    this.ingesterClient = ingesterClient;
  }

  public GetMetricsResponse queryMetrics(GetMetricsRequest getMetricsRequest) {
    return ingesterClient.query(getMetricsRequest);
  }

  public GetMetricsHintsResponse getMetricHints(GetMetricNameHints request) {
    return ingesterClient.getMetricHints(request);
  }

  public GetMetricsHintsResponse getTagHints(GetTagHintsRequest request) {
    return ingesterClient.getTagHints(request);
  }

  public GetMetricsHintsResponse getTagValueHints(GetTagValueHintsRequest request) {
    return ingesterClient.getTagValueHints(request);
  }

  public GetMetricsBatchResponse queryMetrics(MultiQueryRequest multiQueryRequest)
      throws MalformedQueryException {
    var fetchers = new ArrayList<Supplier<GetMetricsResponse>>();
    for (var constrained : hydrateMetricQueries(multiQueryRequest)) {
      fetchers.add(() -> ingesterClient.query(constrained));
    }
    var results =
        this.parallelExecutor.submit(fetchers, Duration.of(this.timeoutMillis, ChronoUnit.MILLIS));
    return new GetMetricsBatchResponse(results);
  }

  public GetExemplarsResponse getExemplarsResponse(GetExemplarsRequest request) {
    return ingesterClient.getExemplarsResponse(request);
  }

  /** Queries exemplars using the same variable-aware metric query contract as metric batches. */
  public GetExemplarsBatchResponse queryExemplars(MultiQueryRequest multiQueryRequest)
      throws MalformedQueryException {
    if (multiQueryRequest.getGrammar() != GRAMMAR.OKAPI_JSON) {
      throw new BadRequestException("Exemplar queries require the OKAPI_JSON grammar.");
    }

    var fetchers = new ArrayList<Supplier<GetExemplarsResponse>>();
    for (var query : hydrateMetricQueries(multiQueryRequest)) {
      var exemplarRequest =
          GetExemplarsRequest.builder()
              .metric(query.getMetric())
              .labels(query.getTags())
              .timeFilter(
                  TimestampFilter.builder()
                      .tsStartNanos(Math.multiplyExact(query.getStart(), 1_000_000L))
                      .tsEndNanos(Math.multiplyExact(query.getEnd(), 1_000_000L))
                      .build())
              .build();
      fetchers.add(() -> ingesterClient.getExemplarsResponse(exemplarRequest));
    }

    var results =
        parallelExecutor.submit(fetchers, Duration.of(this.timeoutMillis, ChronoUnit.MILLIS));
    return new GetExemplarsBatchResponse(results);
  }

  private List<GetMetricsRequest> hydrateMetricQueries(MultiQueryRequest multiQueryRequest)
      throws MalformedQueryException {
    var vars =
        multiQueryRequest.getVarsContext() == null
            ? Map.<String, String>of()
            : multiQueryRequest.getVarsContext().getVarValues();
    var constrainedQueries = new ArrayList<GetMetricsRequest>();
    for (var rawQuery : multiQueryRequest.getQueries()) {
      var substituted = VarsPreprocessor.substituteVars(rawQuery.getQuery(), vars);
      var parsed = gson.fromJson(substituted, GetMetricsRequest.class);
      constrainedQueries.add(
          ConstraintEnforcer.applyTimeConstraint(parsed, multiQueryRequest.getTimeConstraint()));
    }
    return constrainedQueries;
  }

  @PreDestroy
  public void tearDown() throws IOException {
    this.parallelExecutor.close();
  }
}

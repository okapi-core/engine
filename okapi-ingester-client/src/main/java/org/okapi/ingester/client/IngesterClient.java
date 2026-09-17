/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.ingester.client;

import com.google.gson.Gson;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import java.io.IOException;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.okapi.rest.logs.*;
import org.okapi.rest.metrics.exemplar.GetExemplarsRequest;
import org.okapi.rest.metrics.exemplar.GetExemplarsResponse;
import org.okapi.rest.metrics.query.GetMetricsRequest;
import org.okapi.rest.metrics.query.GetMetricsResponse;
import org.okapi.rest.search.*;
import org.okapi.rest.traces.*;
import org.okapi.rest.traces.red.ListServicesRequest;
import org.okapi.rest.traces.red.ServiceListResponse;
import org.okapi.rest.traces.red.ServiceRedRequest;
import org.okapi.rest.traces.red.ServiceRedResponse;

public class IngesterClient {

  String endpoint;
  Gson gson;
  OkHttpClient client;
  ProxyResponseTranslator translator;

  public IngesterClient(String endpoint, OkHttpClient client, ProxyResponseTranslator translator) {
    this.endpoint = endpoint;
    this.gson = new Gson();
    this.client = client;
    this.translator = translator;
  }

  private <T> T postRequest(String path, Object requestBody, Class<T> clazz) {
    RequestBody body = RequestBody.create(gson.toJson(requestBody).getBytes());
    Request request =
        new Request.Builder()
            .url(ClientUrls.concat(endpoint, path))
            .header("Content-Type", "application/json")
            .post(body)
            .build();
    try (var response = client.newCall(request).execute()) {
      return translator.translateResponse(response, clazz);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public GetMetricsHintsResponse getMetricHints(GetMetricNameHints request) {
    return postRequest("/api/v1/metrics/name/hints", request, GetMetricsHintsResponse.class);
  }

  public GetMetricsHintsResponse getTagHints(GetTagHintsRequest request) {
    return postRequest("/api/v1/metrics/tag/hints", request, GetMetricsHintsResponse.class);
  }

  public GetMetricsHintsResponse getTagValueHints(GetTagValueHintsRequest request) {
    return postRequest("/api/v1/metrics/tag-value/hints", request, GetMetricsHintsResponse.class);
  }

  public GetMetricsResponse query(GetMetricsRequest getMetricsRequest) {
    return postRequest("/api/v1/metrics/query", getMetricsRequest, GetMetricsResponse.class);
  }

  public GetExemplarsResponse getExemplarsResponse(GetExemplarsRequest request) {
    return postRequest("/api/v1/metrics/exemplars", request, GetExemplarsResponse.class);
  }

  public SpanQueryV2Response querySpans(SpanQueryV2Request request) {
    return postRequest("/api/v1/spans/query", request, SpanQueryV2Response.class);
  }

  public SpanQueryV2SummaryResponse getResultsSummary(SpanQueryV2Request request) {
    return postRequest("/api/v1/spans/query/summary", request, SpanQueryV2SummaryResponse.class);
  }

  public SpansFlameGraphResponse querySpansFlameGraph(SpanQueryV2Request request) {
    return postRequest("/api/v1/spans/flamegraph", request, SpansFlameGraphResponse.class);
  }

  public SpansQueryStatsResponse getSpansStats(SpansQueryStatsRequest request) {
    return postRequest("/api/v1/spans/stats", request, SpansQueryStatsResponse.class);
  }

  public SpanAttributeHintsResponse getSpanAttributeHints(SpanAttributeHintsRequest request) {
    return postRequest("/api/v1/spans/attributes/hints", request, SpanAttributeHintsResponse.class);
  }

  public ServiceListResponse getSvcList(ListServicesRequest request) {
    return postRequest("/api/v1/services", request, ServiceListResponse.class);
  }

  public ServiceRedResponse getServiceReds(ServiceRedRequest request) {
    return postRequest("/api/v1/reds", request, ServiceRedResponse.class);
  }

  public SpanAttributeValueHintsResponse getSpanAttributeValueHints(
      SpanAttributeValueHintsRequest request) {
    return postRequest(
        "/api/v1/spans/attributes/values/hints", request, SpanAttributeValueHintsResponse.class);
  }

  public SearchMetricsV2Response searchMetrics(SearchMetricsRequest request) {
    return postRequest("/api/v1/metrics/search", request, SearchMetricsV2Response.class);
  }

  public ChLogsQueryResponse searchLogs(ChLogsQueryRequest request) {
    return postRequest("/api/v1/logs/query", request, ChLogsQueryResponse.class);
  }

  public ChLogsFieldsResponse getLogsFields(ChLogsFieldsRequest request) {
    return postRequest("/api/v1/logs/fields", request, ChLogsFieldsResponse.class);
  }

  public ChLogsFieldValuesResponse getLogsFieldValues(ChLogsFieldValuesRequest request) {
    return postRequest("/api/v1/logs/field-values", request, ChLogsFieldValuesResponse.class);
  }

  public OkapiLogQlResponse queryLogsQl(OkapiLogQlRequest request) {
    return postRequest("/api/v1/logs/query/logql", request, OkapiLogQlResponse.class);
  }

  public OkapiTraceQlResponse queryTraceQl(OkapiTraceQlRequest request) {
    return postRequest("/api/v1/spans/query/traceql", request, OkapiTraceQlResponse.class);
  }

  public ChLogsSummaryResponse getLogsSummary(ChLogsSummaryRequest request) {
    return postRequest("/api/v1/logs/summary", request, ChLogsSummaryResponse.class);
  }

  public void ingestOtelMetrics(ExportMetricsServiceRequest request) {
    var ep = endpoint + "/v1/metrics";
    ingestPayload(ep, request.toByteArray());
  }

  public void ingestOtelTraces(ExportTraceServiceRequest request) {
    var ep = endpoint + "/v1/traces";
    ingestPayload(ep, request.toByteArray());
  }

  private void ingestPayload(String endpoint, byte[] payload) {
    RequestBody body = RequestBody.create(payload);
    Request request =
        new Request.Builder()
            .url(endpoint)
            .header("Content-Type", "application/octet-stream")
            .post(body)
            .build();
    try (var response = client.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        throw new RuntimeException("Ingester OTEL ingest failed: " + response.code());
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}

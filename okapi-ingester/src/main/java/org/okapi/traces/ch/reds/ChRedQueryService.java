/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.traces.ch.reds;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.query.GenericRecord;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.okapi.ch.ChTemplateFiles;
import org.okapi.metrics.ch.ChConstants;
import org.okapi.metrics.pojos.RES_TYPE;
import org.okapi.parallel.ParallelExecutor;
import org.okapi.rest.traces.red.*;
import org.okapi.spring.configs.Profiles;
import org.okapi.traces.ch.template.*;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile(Profiles.PROFILE_CH)
public class ChRedQueryService {
  private static final String DURATION_MS_EXPR = "(ts_end_nanos - ts_start_nanos) / 1000000.0";
  private static final int TOTAL_OPS_SUMMARY_LIMIT = 20;
  private static final int PARALLEL_THROTTLE_LIMIT = 8;
  private static final int PARALLEL_POOL_SIZE = 8;
  private static final Duration PARALLEL_WAIT_TIME = Duration.ofSeconds(10);
  private static final RedMetrics EMPTY =
      RedMetrics.builder()
          .ts(List.of())
          .counts(List.of())
          .rps(List.of())
          .rpm(List.of())
          .errorRates(List.of())
          .errors(List.of())
          .durationsP50(List.of())
          .durationsP75(List.of())
          .durationsP90(List.of())
          .durationsP99(List.of())
          .totalRequests(0L)
          .totalErrors(0L)
          .build();

  private final Client client;
  private final ChTraceTemplateEngine engine;
  private final ParallelExecutor parallelExecutor;

  public ChRedQueryService(Client client, ChTraceTemplateEngine engine) {
    this.client = client;
    this.engine = engine;
    this.parallelExecutor =
        new ParallelExecutor(PARALLEL_THROTTLE_LIMIT, PARALLEL_POOL_SIZE, PARALLEL_WAIT_TIME);
  }

  public ServiceRedResponse queryRed(ServiceRedRequest request) {
    var serviceRed = getServiceLevelRed(request);
    var peerReds = getServiceEdgeRed(request);
    var opReds = queryOpsReds(request);
    var totalDetectedOps = getTotalDetectedOps(request);
    return ServiceRedResponse.builder()
        .service(request.getService())
        .serviceRed(serviceRed)
        .peerReds(peerReds)
        .serviceOpReds(opReds)
        .totalDetectedOps(totalDetectedOps)
        .build();
  }

  protected List<ServiceOpRed> queryOpsReds(ServiceRedRequest request) {
    var detected = getDetectedOps(request);
    if (detected.isEmpty()) {
      return List.of();
    }
    var suppliers = new ArrayList<Supplier<ServiceOpRed>>(detected.size());
    for (var op : detected) {
      suppliers.add(() -> queryOpReds(request, op));
    }
    return parallelExecutor.submit(suppliers);
  }

  protected ServiceOpRed queryOpReds(ServiceRedRequest request, String op) {
    if (request == null || request.getService() == null || request.getService().isEmpty()) {
      return ServiceOpRed.builder().op(op).redMetrics(EMPTY).build();
    }
    if (op == null || op.isEmpty()) {
      return ServiceOpRed.builder().op(op).redMetrics(EMPTY).build();
    }
    var template =
        ChServiceRedOpMetricsTemplate.builder()
            .table(ChConstants.TBL_SERVICE_RED_EVENTS)
            .serviceName(request.getService())
            .spanName(op)
            .bucketStartExpr(buildBucketStartExpr(request.getResType()))
            .durationExpr(DURATION_MS_EXPR)
            .timestampFilter(request.getTimestampFilter())
            .build();
    var query = engine.render(ChTemplateFiles.GET_SERVICE_RED_OP_METRICS, template);
    var records = client.queryAll(query);
    if (records.isEmpty()) {
      return ServiceOpRed.builder().op(op).redMetrics(EMPTY).build();
    }
    var red = buildRedMetrics(request, records);
    return ServiceOpRed.builder().op(op).redMetrics(red).build();
  }

  protected List<String> getDetectedOps(ServiceRedRequest request) {
    if (request == null || request.getService() == null || request.getService().isEmpty()) {
      return List.of();
    }
    var template =
        ChServiceRedOpsTemplate.builder()
            .table(ChConstants.TBL_SERVICE_RED_EVENTS)
            .serviceName(request.getService())
            .timestampFilter(request.getTimestampFilter())
            .limit(TOTAL_OPS_SUMMARY_LIMIT)
            .build();
    var query = engine.render(ChTemplateFiles.GET_SERVICE_RED_OPS, template);
    var records = client.queryAll(query);
    var ops = new ArrayList<String>(records.size());
    for (var record : records) {
      var op = record.getString("span_name");
      if (op != null && !op.isEmpty()) {
        ops.add(op);
      }
    }
    return ops;
  }

  protected Integer getTotalDetectedOps(ServiceRedRequest request) {
    if (request == null || request.getService() == null || request.getService().isEmpty()) {
      return 0;
    }
    var template =
        ChServiceRedOpsCountTemplate.builder()
            .table(ChConstants.TBL_SERVICE_RED_EVENTS)
            .serviceName(request.getService())
            .timestampFilter(request.getTimestampFilter())
            .build();
    var query = engine.render(ChTemplateFiles.GET_SERVICE_RED_OPS_COUNT, template);
    var records = client.queryAll(query);
    if (records.isEmpty()) {
      return 0;
    }
    return (int) records.getFirst().getLong("total_ops");
  }

  public ServiceListResponse queryServiceList(ListServicesRequest request) {
    if (request == null || request.getTimestampFilter() == null) {
      return ServiceListResponse.builder().services(List.of()).build();
    }
    var template =
        ChServiceRedServicesTemplate.builder()
            .table(ChConstants.TBL_SERVICE_RED_EVENTS)
            .timestampFilter(request.getTimestampFilter())
            .build();
    var query = engine.render(ChTemplateFiles.GET_SERVICE_RED_SERVICES, template);
    var records = client.queryAll(query);
    var services = new ArrayList<String>(records.size());
    for (var record : records) {
      var service = record.getString("service_name");
      if (service != null && !service.isEmpty()) {
        services.add(service);
      }
    }
    return ServiceListResponse.builder().services(services).build();
  }

  protected RedMetrics getServiceLevelRed(ServiceRedRequest request) {
    return queryRedMetrics(request, null);
  }

  protected List<String> getPeers(ServiceRedRequest request) {
    if (request == null || request.getService() == null || request.getService().isEmpty()) {
      return List.of();
    }
    var template =
        ChServiceRedPeersTemplate.builder()
            .table(ChConstants.TBL_SERVICE_RED_EVENTS)
            .serviceName(request.getService())
            .timestampFilter(request.getTimestampFilter())
            .build();
    var query = engine.render(ChTemplateFiles.GET_SERVICE_RED_PEERS, template);
    var records = client.queryAll(query);
    var peers = new ArrayList<String>(records.size());
    for (var record : records) {
      var peer = record.getString("peer_service_name");
      if (peer != null && !peer.isEmpty()) {
        peers.add(peer);
      }
    }
    return peers;
  }

  protected List<ServiceEdgeRed> getServiceEdgeRed(ServiceRedRequest req) {
    var peers = getPeers(req);
    if (peers.isEmpty()) {
      return List.of();
    }
    var suppliers = new ArrayList<Supplier<ServiceEdgeRed>>(peers.size());
    for (var peer : peers) {
      suppliers.add(
          () -> {
            var redMetrics = queryRedMetrics(req, peer);
            return ServiceEdgeRed.builder().peerService(peer).redMetrics(redMetrics).build();
          });
    }
    return parallelExecutor.submit(suppliers);
  }

  private RedMetrics queryRedMetrics(ServiceRedRequest request, String peerService) {
    var template =
        ChServiceRedMetricsTemplate.builder()
            .table(ChConstants.TBL_SERVICE_RED_EVENTS)
            .serviceName(request.getService())
            .peerServiceName(peerService)
            .bucketStartExpr(buildBucketStartExpr(request.getResType()))
            .durationExpr(DURATION_MS_EXPR)
            .timestampFilter(request.getTimestampFilter())
            .build();
    var query = engine.render(ChTemplateFiles.GET_SERVICE_RED_METRICS, template);
    var records = client.queryAll(query);
    if (records.isEmpty()) {
      return EMPTY;
    }
    return buildRedMetrics(request, records);
  }

  private RedMetrics buildRedMetrics(ServiceRedRequest request, List<GenericRecord> records) {
    var ts = new ArrayList<Long>(records.size());
    var counts = new ArrayList<Long>(records.size());
    var rps = new ArrayList<Double>(records.size());
    var rpm = new ArrayList<Double>(records.size());
    var errorRates = new ArrayList<Double>(records.size());
    var errors = new ArrayList<Long>(records.size());
    var p50 = new ArrayList<Double>(records.size());
    var p75 = new ArrayList<Double>(records.size());
    var p90 = new ArrayList<Double>(records.size());
    var p99 = new ArrayList<Double>(records.size());
    var bucketSeconds = bucketSeconds(request == null ? null : request.getResType());
    long totalRequests = 0;
    long totalErrors = 0;
    for (var record : records) {
      var count = record.getLong("total_count");
      var errorCount = record.getLong("error_count");
      ts.add(record.getLong("bucket_start_ms"));
      counts.add(count);
      rps.add(count / bucketSeconds);
      rpm.add(count * 60.0 / bucketSeconds);
      errorRates.add(count == 0 ? 0.0 : errorCount / (double) count);
      errors.add(errorCount);
      p50.add(record.getDouble("duration_p50"));
      p75.add(record.getDouble("duration_p75"));
      p90.add(record.getDouble("duration_p90"));
      p99.add(record.getDouble("duration_p99"));
      totalRequests += count;
      totalErrors += errorCount;
    }
    return RedMetrics.builder()
        .ts(ts)
        .counts(counts)
        .rps(rps)
        .rpm(rpm)
        .errorRates(errorRates)
        .errors(errors)
        .durationsP50(p50)
        .durationsP75(p75)
        .durationsP90(p90)
        .durationsP99(p99)
        .totalRequests(totalRequests)
        .totalErrors(totalErrors)
        .availability(totalRequests == 0 ? null : 1.0 - totalErrors / (double) totalRequests)
        .build();
  }

  private static double bucketSeconds(RES_TYPE resType) {
    var effective = resType == null ? RES_TYPE.SECONDLY : resType;
    return switch (effective) {
      case SECONDLY -> 1.0;
      case MINUTELY -> 60.0;
      case HOURLY -> 3600.0;
    };
  }

  private static String buildBucketStartExpr(RES_TYPE resType) {
    var effective = resType == null ? RES_TYPE.SECONDLY : resType;
    return switch (effective) {
      case SECONDLY ->
          "toUnixTimestamp(toStartOfSecond(toDateTime64(ts_start_nanos/1000000000, 0))) * 1000";
      case MINUTELY ->
          "toUnixTimestamp(toStartOfMinute(toDateTime(ts_start_nanos/1000000000))) * 1000";
      case HOURLY -> "toUnixTimestamp(toStartOfHour(toDateTime(ts_start_nanos/1000000000))) * 1000";
    };
  }

  @PreDestroy
  public void shutdown() {
    try {
      parallelExecutor.close();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}

/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.logs.ch;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.query.GenericRecord;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.okapi.metrics.ch.ChConstants;
import org.okapi.rest.logs.ChLogsFacetCount;
import org.okapi.rest.logs.ChLogsServiceSeveritySummary;
import org.okapi.rest.logs.ChLogsSeverityCount;
import org.okapi.rest.logs.ChLogsSeverityTimelinePoint;
import org.okapi.rest.logs.ChLogsSummaryRequest;
import org.okapi.rest.logs.ChLogsSummaryResponse;
import org.okapi.rest.logs.ChLogsTimelinePoint;
import org.okapi.spring.configs.Profiles;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile(Profiles.PROFILE_CH)
@RequiredArgsConstructor
public class ChLogsSummaryService {
  private static final int DEFAULT_LIMIT = 20;
  private static final int MAX_LIMIT = 100;
  private static final long DEFAULT_BUCKET_MS = 60_000L;
  private static final long MIN_BUCKET_MS = 1_000L;

  private final Client client;

  public ChLogsSummaryResponse getSummary(ChLogsSummaryRequest request) {
    var limit = limit(request.getLimit());
    var bucketMillis = bucketMillis(request);
    var where =
        ChLogsQueryBuilder.buildWhereClause(
            request.getTsStartNanos(), request.getTsEndNanos(), request.getFilters());

    return ChLogsSummaryResponse.builder()
        .count(queryCount(where))
        .bucketMillis(bucketMillis)
        .volume(queryVolume(where, bucketMillis))
        .severityTimeline(querySeverityTimeline(where, bucketMillis))
        .severityDistribution(querySeverityDistribution(where))
        .topServices(queryFacet(where, "service_name", limit))
        .topStreams(queryFacet(where, "log_stream", limit))
        .serviceSeverity(queryServiceSeverity(where, limit))
        .build();
  }

  private long queryCount(String where) {
    var records =
        client.queryAll(
            "SELECT count() AS count FROM " + ChConstants.TBL_LOGS_V1 + " WHERE " + where);
    if (records.isEmpty()) {
      return 0;
    }
    return records.getFirst().getLong("count");
  }

  private List<ChLogsTimelinePoint> queryVolume(String where, long bucketMillis) {
    var query =
        """
        SELECT intDiv(intDiv(ts_ns, 1000000), %d) * %d AS bucket_start_ms, count() AS count
        FROM %s
        WHERE %s
        GROUP BY bucket_start_ms
        ORDER BY bucket_start_ms
        """
            .formatted(bucketMillis, bucketMillis, ChConstants.TBL_LOGS_V1, where);
    var records = client.queryAll(StringUtils.normalizeSpace(query));
    var out = new ArrayList<ChLogsTimelinePoint>(records.size());
    for (var record : records) {
      out.add(
          ChLogsTimelinePoint.builder()
              .bucketStartMs(record.getLong("bucket_start_ms"))
              .count(record.getLong("count"))
              .build());
    }
    return out;
  }

  private List<ChLogsSeverityTimelinePoint> querySeverityTimeline(String where, long bucketMillis) {
    var query =
        """
        SELECT
          intDiv(intDiv(ts_ns, 1000000), %d) * %d AS bucket_start_ms,
          log_level,
          %s AS severity,
          count() AS count
        FROM %s
        WHERE %s
        GROUP BY bucket_start_ms, log_level, severity
        ORDER BY bucket_start_ms, log_level
        """
            .formatted(
                bucketMillis,
                bucketMillis,
                ChLogsQueryBuilder.severityExpr(),
                ChConstants.TBL_LOGS_V1,
                where);
    var records = client.queryAll(StringUtils.normalizeSpace(query));
    var out = new ArrayList<ChLogsSeverityTimelinePoint>(records.size());
    for (var record : records) {
      var logLevel = (int) record.getLong("log_level");
      out.add(
          ChLogsSeverityTimelinePoint.builder()
              .bucketStartMs(record.getLong("bucket_start_ms"))
              .logLevel(logLevel)
              .severity(record.getString("severity"))
              .count(record.getLong("count"))
              .build());
    }
    return out;
  }

  private List<ChLogsSeverityCount> querySeverityDistribution(String where) {
    var query =
        """
        SELECT log_level, %s AS severity, count() AS count
        FROM %s
        WHERE %s
        GROUP BY log_level, severity
        ORDER BY log_level
        """
            .formatted(ChLogsQueryBuilder.severityExpr(), ChConstants.TBL_LOGS_V1, where);
    var records = client.queryAll(StringUtils.normalizeSpace(query));
    var out = new ArrayList<ChLogsSeverityCount>(records.size());
    for (var record : records) {
      var logLevel = (int) record.getLong("log_level");
      out.add(
          ChLogsSeverityCount.builder()
              .logLevel(logLevel)
              .severity(record.getString("severity"))
              .count(record.getLong("count"))
              .build());
    }
    return out;
  }

  private List<ChLogsFacetCount> queryFacet(String where, String column, int limit) {
    var query =
        """
        SELECT %s AS value, count() AS count
        FROM %s
        WHERE %s
        GROUP BY value
        ORDER BY count DESC, value ASC
        LIMIT %d
        """
            .formatted(column, ChConstants.TBL_LOGS_V1, where, limit);
    var records = client.queryAll(StringUtils.normalizeSpace(query));
    var out = new ArrayList<ChLogsFacetCount>(records.size());
    for (var record : records) {
      out.add(
          ChLogsFacetCount.builder()
              .value(record.getString("value"))
              .count(record.getLong("count"))
              .build());
    }
    return out;
  }

  private List<ChLogsServiceSeveritySummary> queryServiceSeverity(String where, int limit) {
    var query =
        """
        SELECT
          service_name,
          count() AS total,
          countIf(log_level >= 1 AND log_level <= 4) AS trace,
          countIf(log_level >= 5 AND log_level <= 8) AS debug,
          countIf(log_level >= 9 AND log_level <= 12) AS info,
          countIf(log_level >= 13 AND log_level <= 16) AS warn,
          countIf(log_level >= 17 AND log_level <= 20) AS error,
          countIf(log_level >= 21) AS fatal,
          if(total = 0, 0, (error + fatal) / total) AS error_ratio
        FROM %s
        WHERE %s
        GROUP BY service_name
        ORDER BY error + fatal DESC, total DESC, service_name ASC
        LIMIT %d
        """
            .formatted(ChConstants.TBL_LOGS_V1, where, limit);
    var records = client.queryAll(StringUtils.normalizeSpace(query));
    var out = new ArrayList<ChLogsServiceSeveritySummary>(records.size());
    for (var record : records) {
      out.add(toServiceSeverity(record));
    }
    return out;
  }

  private ChLogsServiceSeveritySummary toServiceSeverity(GenericRecord record) {
    return ChLogsServiceSeveritySummary.builder()
        .serviceName(record.getString("service_name"))
        .total(record.getLong("total"))
        .trace(record.getLong("trace"))
        .debug(record.getLong("debug"))
        .info(record.getLong("info"))
        .warn(record.getLong("warn"))
        .error(record.getLong("error"))
        .fatal(record.getLong("fatal"))
        .errorRatio(record.getDouble("error_ratio"))
        .build();
  }

  private int limit(Integer requestedLimit) {
    if (requestedLimit == null) {
      return DEFAULT_LIMIT;
    }
    return Math.min(requestedLimit, MAX_LIMIT);
  }

  private long bucketMillis(ChLogsSummaryRequest request) {
    if (request.getBucketMillis() != null) {
      return request.getBucketMillis();
    }
    if (request.getTsStartNanos() == null || request.getTsEndNanos() == null) {
      return DEFAULT_BUCKET_MS;
    }
    var rangeMs = Math.max(1L, (request.getTsEndNanos() - request.getTsStartNanos()) / 1_000_000L);
    return Math.max(MIN_BUCKET_MS, rangeMs / 120L);
  }
}

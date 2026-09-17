/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.metrics.ch;

import static org.okapi.validation.OkapiChecks.checkArgument;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.query.GenericRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.okapi.ch.ChTemplateFiles;
import org.okapi.exceptions.BadRequestException;
import org.okapi.metrics.ch.template.ChGetGaugeQueryTemplate;
import org.okapi.metrics.ch.template.ChMetricTemplateEngine;
import org.okapi.metrics.pojos.AGG_TYPE;
import org.okapi.metrics.pojos.RES_TYPE;
import org.okapi.rest.metrics.query.*;

/** Handles gauge query execution against ClickHouse. */
@Slf4j
public class GaugeQueryProcessor {
  private final Client client;
  private final ChMetricTemplateEngine templateEngine;

  public GaugeQueryProcessor(Client client, ChMetricTemplateEngine templateEngine) {
    this.client = client;
    this.templateEngine = templateEngine;
  }

  public GetMetricsResponse getGaugeRes(GetMetricsRequest getMetricsRequest) {
    checkArgument(
        getMetricsRequest.getGaugeQueryConfig() != null,
        () -> new BadRequestException("gaugeQueryConfig is required for GAUGE"));
    var gaugeQueryConfig = getMetricsRequest.getGaugeQueryConfig();

    checkArgument(
        getMetricsRequest.getGaugeQueryConfig().getAggregation() != null,
        () -> new BadRequestException("An aggregation is required for gauge queries."));
    checkArgument(
        getMetricsRequest.getGaugeQueryConfig().getResolution() != null,
        () -> new BadRequestException("A resolution is required for gauge queries."));
    var aggType = gaugeQueryConfig.getAggregation();
    var resType = gaugeQueryConfig.getResolution();
    var templateModel =
        ChGetGaugeQueryTemplate.builder()
            .table(ChConstants.TBL_GAUGES)
            .bucketExpr(bucketExpr(resType))
            .aggExpr(aggExpr(aggType))
            .metric(getMetricsRequest.getMetric())
            .startMs(getMetricsRequest.getStart())
            .endMs(getMetricsRequest.getEnd())
            .tags(getMetricsRequest.getTags())
            .build();

    var query = templateEngine.render(ChTemplateFiles.GET_GAUGE_SAMPLES, templateModel);
    List<GenericRecord> records = client.queryAll(query);
    if (records.isEmpty()) {
      return CannedResponses.noMetricsResponse(
          getMetricsRequest.getMetric(), getMetricsRequest.getTags());
    }

    var seriesList = new ArrayList<GaugeSeries>();
    Map<String, String> currentTags = null;
    String currentUnit = null;
    var currentTimes = new ArrayList<Long>();
    var currentValues = new ArrayList<Float>();
    for (var record : records) {
      @SuppressWarnings("unchecked")
      var tags = (Map<String, String>) record.getObject("tags");
      var unit = (String) record.getObject("unit");

      if (!Objects.equals(tags, currentTags) || !Objects.equals(unit, currentUnit)) {
        if (currentTags != null) {
          seriesList.add(
              GaugeSeries.builder()
                  .unit(currentUnit)
                  .tags(currentTags)
                  .times(currentTimes)
                  .values(currentValues)
                  .build());
        }
        currentTags = tags;
        currentUnit = unit;
        currentTimes = new ArrayList<>();
        currentValues = new ArrayList<>();
      }
      currentTimes.add(record.getLong("bucket_ms"));
      currentValues.add((float) record.getDouble("value"));
    }
    seriesList.add(
        GaugeSeries.builder()
            .tags(currentTags)
            .times(currentTimes)
            .unit(currentUnit)
            .values(currentValues)
            .build());

    var gaugeResponse =
        GetGaugeResponse.builder()
            .resolution(resType)
            .aggregation(aggType)
            .series(seriesList)
            .build();

    return GetMetricsResponse.builder()
        .metric(getMetricsRequest.getMetric())
        .tags(getMetricsRequest.getTags())
        .gaugeResponse(gaugeResponse)
        .build();
  }

  private static String bucketExpr(RES_TYPE resType) {
    return switch (resType) {
      case SECONDLY -> "toUnixTimestamp64Milli(toStartOfSecond(timestamp))";
      case MINUTELY -> "toUnixTimestamp(toStartOfMinute(timestamp)) * 1000";
      case HOURLY -> "toUnixTimestamp(toStartOfHour(timestamp)) * 1000";
    };
  }

  private static String aggExpr(AGG_TYPE aggType) {
    return switch (aggType) {
      case AVG -> "avg(value)";
      case SUM -> "sum(value)";
      case MIN -> "min(value)";
      case MAX -> "max(value)";
      case COUNT -> "count()";
      case P50 -> "quantile(0.5)(value)";
      case P75 -> "quantile(0.75)(value)";
      case P90 -> "quantile(0.9)(value)";
      case P95 -> "quantile(0.95)(value)";
      case P99 -> "quantile(0.99)(value)";
    };
  }
}

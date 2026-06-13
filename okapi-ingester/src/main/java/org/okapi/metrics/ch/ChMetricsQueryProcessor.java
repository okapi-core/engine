/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.metrics.ch;

import com.clickhouse.client.api.Client;
import gg.jte.TemplateOutput;
import gg.jte.output.StringOutput;
import org.okapi.ch.ChTemplateFiles;
import org.okapi.exceptions.BadRequestException;
import org.okapi.metrics.ch.template.ChMetricTemplateEngine;
import org.okapi.rest.metrics.exemplar.GetExemplarsRequest;
import org.okapi.rest.metrics.exemplar.GetExemplarsResponse;
import org.okapi.rest.metrics.query.GetMetricsRequest;
import org.okapi.rest.metrics.query.GetMetricsResponse;
import org.okapi.rest.search.*;
import org.okapi.usermessages.UserFacingMessages;
import org.okapi.validation.OkapiChecks;

import java.util.ArrayList;
import java.util.Objects;

/** Routes metric queries to specific processors for gauges, histograms, and sums. */
public class ChMetricsQueryProcessor {
  private final Client client;
  private final GaugeQueryProcessor gaugeQueryProcessor;
  private final HistogramQueryProcessor histogramQueryProcessor;
  private final SumQueryProcessor sumQueryProcessor;
  private final ChExemplarQueryProcessor exemplarQueryProcessor;
  private final ChMetricTemplateEngine templateEngine;

  public ChMetricsQueryProcessor(Client client, ChMetricTemplateEngine templateEngine) {
    this.client = client;
    this.templateEngine = templateEngine;
    this.gaugeQueryProcessor = new GaugeQueryProcessor(client, templateEngine);
    this.histogramQueryProcessor = new HistogramQueryProcessor(client, templateEngine);
    this.sumQueryProcessor = new SumQueryProcessor(client, templateEngine);
    this.exemplarQueryProcessor = new ChExemplarQueryProcessor(client, templateEngine);
  }

  public GetMetricsResponse getMetricsResponse(GetMetricsRequest getMetricsRequest) {
    return switch (Objects.requireNonNull(getMetricsRequest.getMetricType())) {
      case GAUGE -> gaugeQueryProcessor.getGaugeRes(getMetricsRequest);
      case HISTO -> histogramQueryProcessor.getHistoRes(getMetricsRequest);
      case SUM -> sumQueryProcessor.getSumRes(getMetricsRequest);
      default -> throw new IllegalArgumentException("Unsupported metric type.");
    };
  }

  public GetMetricsHintsResponse getMetricHints(GetMetricNameHints request) {
    OkapiChecks.checkArgument(
        request.getInterval() != null,
        () -> new BadRequestException(UserFacingMessages.TIME_FILTER_MISSING));
    var interval = request.getInterval();
    var metricType =
        request.getMetricEventFilter() == null
            ? null
            : request.getMetricEventFilter().getMetricType();
    var query =
        renderQuery(
            ChTemplateFiles.GET_METRIC_HINTS,
            MetricHintsQueryTemplate.builder()
                .table(ChConstants.TBL_METRIC_EVENTS_META)
                .eventType(metricType == null ? null : metricType.name())
                .metricPrefix(request.getMetricPrefix() == null ? "" : request.getMetricPrefix())
                .startMs(interval.getStart())
                .endMs(interval.getEnd())
                .limit(ChConstants.METRIC_HINTS_LIMIT)
                .build());

    var records = client.queryAll(query);
    var completions = new ArrayList<String>(records.size());
    for (var record : records) {
      completions.add(record.getString("metric"));
    }
    return GetMetricsHintsResponse.builder().metricHints(completions).build();
  }

  public GetMetricsHintsResponse getTagHints(GetTagHintsRequest request) {
    var interval = request.getInterval();
    var filter = request.getMetricEventFilter();
    var metricType = filter == null ? null : filter.getMetricType();

    var query =
        renderQuery(
            ChTemplateFiles.GET_TAG_HINTS,
            MetricHintsQueryTemplate.builder()
                .table(ChConstants.TBL_METRIC_EVENTS_META)
                .eventType(metricType == null ? null : metricType.name())
                .metric(request.getMetricName())
                .tagPrefix(request.getTagPrefix() == null ? "" : request.getTagPrefix())
                .otherTags(request.getOtherTags())
                .startMs(interval.getStart())
                .endMs(interval.getEnd())
                .limit(ChConstants.METRIC_HINTS_LIMIT)
                .build());

    var records = client.queryAll(query);
    var completions = new ArrayList<String>(records.size());
    for (var record : records) {
      completions.add(record.getString("tag_key"));
    }
    return GetMetricsHintsResponse.builder().tagHints(completions).build();
  }

  public GetMetricsHintsResponse getTagValueHints(GetTagValueHintsRequest request) {
    Objects.requireNonNull(request, "request is required");
    var interval = Objects.requireNonNull(request.getInterval(), "interval is required");
    var filter = request.getMetricEventFilter();
    var metricType = filter == null ? null : filter.getMetricType();

    var query =
        renderQuery(
            ChTemplateFiles.GET_TAG_VALUE_HINTS,
            MetricHintsQueryTemplate.builder()
                .table(ChConstants.TBL_METRIC_EVENTS_META)
                .eventType(metricType == null ? null : metricType.name())
                .metric(request.getMetricName())
                .tag(request.getTag())
                .otherTags(request.getOtherTags())
                .startMs(interval.getStart())
                .endMs(interval.getEnd())
                .limit(ChConstants.METRIC_HINTS_LIMIT)
                .build());

    var records = client.queryAll(query);
    var candidates = new ArrayList<String>(records.size());
    for (var record : records) {
      candidates.add(record.getString("tag_value"));
    }
    var completion =
        new TagValueCompletion(request.getTag(), candidates, request.getMetricEventFilter());
    return GetMetricsHintsResponse.builder().tagValueHints(completion).build();
  }

  private String renderQuery(String templateName, MetricHintsQueryTemplate data) {
    TemplateOutput output = new StringOutput();
    templateEngine.render(templateName, data, output);
    return output.toString();
  }

  public GetExemplarsResponse getExemplars(GetExemplarsRequest request) {
    return exemplarQueryProcessor.getExemplars(request);
  }
}

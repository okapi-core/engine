/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.metrics.ch;

import com.clickhouse.client.api.Client;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.okapi.ch.ChTemplateFiles;
import org.okapi.metrics.ch.rows.ChSearchMetricsRow;
import org.okapi.metrics.ch.template.ChMetricTemplateEngine;
import org.okapi.metrics.ch.template.ChSearchMetricsQueryTemplate;
import org.okapi.rest.metrics.query.METRIC_TYPE;
import org.okapi.rest.search.MetricPath;
import org.okapi.rest.search.SearchMetricsRequest;
import org.okapi.rest.search.SearchMetricsV2Response;
import org.okapi.spring.configs.Profiles;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@AllArgsConstructor
@Component
@Profile(Profiles.PROFILE_CH)
public class ChSearchMetricsProcessor {
  private final Client client;
  private final ChMetricTemplateEngine templateEngine;

  public SearchMetricsV2Response searchMetricsResponse(SearchMetricsRequest request) {
    var anyMatcher = AnyMetricOrValueFilterMatcher.fromFilter(request.getAnyMetricOrValueFilter());
    var matcher = MetricPathMatcher.fromRequest(request);
    var matchingPaths =
        getMetricsInTimeWindow(request.getTsStartMillis(), request.getTsEndMillis());
    var matching =
        matchingPaths.stream()
            .filter(row -> anyMatcher.matches(row.getName(), row.getTags()))
            .filter(row -> matcher.matches(row.getName(), row.getTags()))
            .map(this::fromPathToRow)
            .toList();
    return SearchMetricsV2Response.builder().matchingPaths(matching).build();
  }

  public MetricPath fromPathToRow(ChSearchMetricsRow row) {
    return MetricPath.builder()
        .metric(row.getName())
        .metricType(row.getMetricType())
        .temporality(row.getTemporality())
        .labels(row.getTags())
        .build();
  }

  public Set<ChSearchMetricsRow> getMetricsInTimeWindow(long tsStartMillis, long tsEndMillis) {
    var template =
        ChSearchMetricsQueryTemplate.builder()
            .table(ChConstants.TBL_METRIC_EVENTS_META)
            .startMs(tsStartMillis)
            .endMs(tsEndMillis)
            .build();
    var query = templateEngine.render(ChTemplateFiles.GET_METRIC_PATHS_IN_RANGE, template);
    try {
      var records = client.queryAll(query);
      var rows = new HashSet<ChSearchMetricsRow>();
      for (var record : records) {
        @SuppressWarnings("unchecked")
        var tags = (Map<String, String>) record.getObject("tags");
        var metricType = METRIC_TYPE.valueOf(record.getString("event_type"));
        var temporality = record.getString("temporality");
        rows.add(
            ChSearchMetricsRow.builder()
                .name(record.getString("metric"))
                .tags(tags)
                .metricType(metricType)
                .temporality(temporality)
                .build());
      }
      return rows;
    } catch (Exception e) {
      log.error("Exception {}\n query: {}", e, query);
      throw new RuntimeException(e);
    }
  }
}

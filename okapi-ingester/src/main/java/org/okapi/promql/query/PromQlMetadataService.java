/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.query;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.query.GenericRecord;
import com.google.inject.Inject;
import gg.jte.TemplateOutput;
import gg.jte.output.StringOutput;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.okapi.ch.ChTemplateFiles;
import org.okapi.metrics.ch.ChConstants;
import org.okapi.metrics.ch.template.ChMetricTemplateEngine;
import org.okapi.promql.ch.ChMetricMetadataQueryTemplate;
import org.okapi.rest.promql.PromQlMetadataItem;
import org.okapi.rest.promql.PromQlMetadataResponse;

public class PromQlMetadataService {

  private static final long LOOKBACK_MS = 15 * 60_000L;
  private final Client client;
  private final ChMetricTemplateEngine templateEngine;

  @Inject
  public PromQlMetadataService(Client client, ChMetricTemplateEngine templateEngine) {
    this.client = client;
    this.templateEngine = templateEngine;
  }

  public PromQlMetadataResponse getMetadata(String metric, Integer limit) {
    long endMs = System.currentTimeMillis();
    long startMs = endMs - LOOKBACK_MS;
    String metricFilter = (metric == null || metric.isBlank()) ? null : metric;
    int effLimit = effectiveLimit(limit);

    TemplateOutput output = new StringOutput();
    templateEngine.render(
        ChTemplateFiles.GET_METRIC_METADATA,
        ChMetricMetadataQueryTemplate.builder()
            .table(ChConstants.TBL_METRIC_EVENTS_META)
            .metric(metricFilter)
            .startMs(startMs)
            .endMs(endMs)
            .limit(effLimit)
            .build(),
        output);
    var query = output.toString();

    List<GenericRecord> records = client.queryAll(query);
    Map<String, List<PromQlMetadataItem>> data = new HashMap<>();
    for (var record : records) {
      String metricName = record.getString("metric");
      String eventType = record.getString("event_type");
      var list = data.computeIfAbsent(metricName, k -> new ArrayList<>());
      String promType = mapType(eventType);
      boolean exists = list.stream().anyMatch(item -> promType.equals(item.getType()));
      if (!exists) {
        list.add(new PromQlMetadataItem(promType, "", ""));
      }
    }

    var resp = new PromQlMetadataResponse();
    resp.setStatus("success");
    resp.setData(data);
    return resp;
  }

  private static int effectiveLimit(Integer limit) {
    int raw = (limit == null || limit <= 0) ? 1000 : limit;
    return Math.min(raw, 1000);
  }

  private static String mapType(String eventType) {
    if (eventType == null) return "unknown";
    return switch (eventType) {
      case "GAUGE" -> "gauge";
      case "SUM" -> "counter";
      case "HISTO" -> "histogram";
      default -> "unknown";
    };
  }
}

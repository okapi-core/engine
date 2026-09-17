/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.overview;

import com.clickhouse.client.api.Client;
import java.time.Clock;
import java.util.Map;
import org.okapi.ch.ChTemplateFiles;
import org.okapi.exceptions.BadRequestException;
import org.okapi.metrics.ch.ChConstants;
import org.okapi.metrics.ch.template.ChMetricTemplateEngine;
import org.okapi.rest.overview.IngesterOverviewRequest;
import org.okapi.rest.overview.IngesterOverviewResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class IngesterOverview {
  private static final Map<String, Long> WINDOWS_MILLIS =
      Map.of(
          "5m", 5 * 60_000L,
          "10m", 10 * 60_000L,
          "30m", 30 * 60_000L,
          "1h", 60 * 60_000L);

  private final Client client;
  private final ChMetricTemplateEngine templateEngine;
  private final Clock clock;

  @Autowired
  public IngesterOverview(Client client, ChMetricTemplateEngine templateEngine) {
    this(client, templateEngine, Clock.systemUTC());
  }

  public IngesterOverview(Client client, ChMetricTemplateEngine templateEngine, Clock clock) {
    this.client = client;
    this.templateEngine = templateEngine;
    this.clock = clock;
  }

  public IngesterOverviewResponse overview(IngesterOverviewRequest request) {
    var window = request == null ? null : request.getWindow();
    var windowMillis = parseWindowMillis(window);
    var endMillis = clock.millis();
    var startMillis = endMillis - windowMillis;
    var row = query(startMillis, endMillis);
    return IngesterOverviewResponse.builder()
        .window(window)
        .startMillis(startMillis)
        .endMillis(endMillis)
        .metricsEvents(row.getMetricsEvents())
        .traceEvents(row.getTraceEvents())
        .logEvents(row.getLogEvents())
        .build();
  }

  private OverviewRow query(long startMillis, long endMillis) {
    var template =
        OverviewQueryTemplate.builder()
            .gaugeTable(ChConstants.TBL_GAUGES)
            .sumTable(ChConstants.TBL_SUM)
            .histoTable(ChConstants.TBL_HISTOS)
            .exponentialHistoTable(ChConstants.TBL_EXPONENTIAL_HISTOS)
            .tracesTable(ChConstants.TBL_SPANS_V1)
            .logsTable(ChConstants.TBL_LOGS_V1)
            .startMillis(startMillis)
            .endMillis(endMillis)
            .startNanos(startMillis * 1_000_000L)
            .endNanos(endMillis * 1_000_000L)
            .build();
    var query = templateEngine.render(ChTemplateFiles.GET_INGESTER_OVERVIEW, template);
    var records = client.queryAll(query);
    if (records.isEmpty()) {
      return OverviewRow.builder().build();
    }
    return OverviewRow.from(records.getFirst());
  }

  private long parseWindowMillis(String window) {
    if (window == null || window.isBlank()) {
      throw new BadRequestException("Overview window is required.");
    }
    var normalized = window.trim();
    var millis = WINDOWS_MILLIS.get(normalized);
    if (millis == null) {
      throw new BadRequestException("Unsupported overview window: " + window);
    }
    return millis;
  }
}

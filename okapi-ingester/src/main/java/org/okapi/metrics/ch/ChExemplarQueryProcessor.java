package org.okapi.metrics.ch;

import com.clickhouse.client.api.Client;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.okapi.ch.ChTemplateFiles;
import org.okapi.metrics.ch.template.ChGetExemplarTemplate;
import org.okapi.metrics.ch.template.ChMetricTemplateEngine;
import org.okapi.rest.metrics.exemplar.GetExemplarsRequest;
import org.okapi.rest.metrics.exemplar.GetExemplarsResponse;

@Slf4j
public class ChExemplarQueryProcessor {
  Client client;
  ChMetricTemplateEngine templateEngine;
  Gson gson;

  public ChExemplarQueryProcessor(Client client, ChMetricTemplateEngine templateEngine) {
    this.client = client;
    this.templateEngine = templateEngine;
    this.gson = new Gson();
  }

  public GetExemplarsResponse getExemplars(GetExemplarsRequest request) {
    var template =
        ChGetExemplarTemplate.builder()
            .fqTable(ChConstants.TBL_EXEMPLAR)
            .tags(request.getLabels())
            .metricName(request.getMetric())
            .tsNanosStart(request.getTimeFilter().getTsStartNanos())
            .tsNanosEnd(request.getTimeFilter().getTsEndNanos())
            .build();
    var query = templateEngine.render(ChTemplateFiles.GET_METRIC_EXEMPLARS, template);
    var records = client.queryAll(query);
    var exemplars =
        records.stream()
            .map(ChExemplarRow::fromRecord)
            .map(row -> row.toRestExemplar(gson))
            .toList();
    return GetExemplarsResponse.builder()
        .exemplars(exemplars)
        .labels(request.getLabels())
        .metric(request.getMetric())
        .timeFilter(request.getTimeFilter())
        .build();
  }
}

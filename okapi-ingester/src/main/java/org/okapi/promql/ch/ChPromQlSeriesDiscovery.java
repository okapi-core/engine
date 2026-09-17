/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.ch;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.query.GenericRecord;
import com.google.re2j.Pattern;
import gg.jte.TemplateOutput;
import gg.jte.output.StringOutput;
import java.util.*;
import lombok.AllArgsConstructor;
import org.okapi.ch.ChSqlEscaper;
import org.okapi.ch.ChTemplateFiles;
import org.okapi.metrics.ch.ChConstants;
import org.okapi.metrics.ch.template.ChMetricTemplateEngine;
import org.okapi.promql.eval.VectorData;
import org.okapi.promql.eval.ts.SeriesDiscovery;
import org.okapi.promql.parse.LabelMatcher;

@AllArgsConstructor
public class ChPromQlSeriesDiscovery implements SeriesDiscovery {
  private final Client client;
  private final ChMetricTemplateEngine templateEngine;

  @Override
  public List<VectorData.SeriesId> expand(
      String metricOrNull, List<LabelMatcher> matchers, long start, long end) {
    TemplateOutput output = new StringOutput();
    templateEngine.render(
        ChTemplateFiles.GET_METRIC_EVENTS_SERIES,
        ChSeriesDiscoveryQueryTemplate.builder()
            .table(ChConstants.TBL_METRIC_EVENTS_META)
            .metric(ChSqlEscaper.escapeLiteral(metricOrNull))
            .startMs(start)
            .endMs(end)
            .build(),
        output);
    var query = output.toString();
    List<GenericRecord> records = client.queryAll(query);
    var out = new ArrayList<VectorData.SeriesId>(records.size());
    for (var record : records) {
      @SuppressWarnings("unchecked")
      var tags = (Map<String, String>) record.getObject("tags");
      var labels = labelsWithUnit(tags, record.getString("unit"));
      var matchLabels = new LinkedHashMap<>(labels);
      var name = record.getString("metric");
      if (name != null) {
        matchLabels.put("__name__", name);
      }
      if (!matches(matchLabels, matchers)) {
        continue;
      }
      var id =
          new VectorData.SeriesId(
              record.getString("metric"),
              new VectorData.Labels(Collections.unmodifiableMap(labels)));
      out.add(id);
    }
    return out;
  }

  static Map<String, String> labelsWithUnit(Map<String, String> tags, String unit) {
    var labels = new LinkedHashMap<String, String>();
    if (tags != null) labels.putAll(tags);
    labels.put("__unit__", unit == null ? "" : unit);
    return labels;
  }

  static boolean matches(Map<String, String> labels, List<LabelMatcher> matchers) {
    if (matchers == null || matchers.isEmpty()) return true;
    for (var matcher : matchers) {
      String value = labels.getOrDefault(matcher.name(), "");
      String matchArg = matcher.value();
      boolean ok =
          switch (matcher.op()) {
            case EQ -> Objects.equals(value, matchArg);
            case NE -> !Objects.equals(value, matchArg);
            case RE -> Pattern.compile(matchArg).matches(value);
            case NRE -> !Pattern.compile(matchArg).matches(value);
          };
      if (!ok) return false;
    }
    return true;
  }
}

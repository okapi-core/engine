/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.metrics.ch;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.query.GenericRecord;
import com.google.common.collect.ArrayListMultimap;
import gg.jte.TemplateOutput;
import gg.jte.output.StringOutput;
import java.util.*;
import org.okapi.ch.ChTemplateFiles;
import org.okapi.metrics.ch.template.ChGetSumQueryTemplate;
import org.okapi.metrics.ch.template.ChMetricTemplateEngine;
import org.okapi.rest.metrics.query.*;
import org.okapi.spring.configs.Profiles;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** Handles sum query execution and aggregation. */
@Service
@Profile(Profiles.PROFILE_CH)
public class SumQueryProcessor {
  private final Client client;
  private final ChMetricTemplateEngine templateEngine;

  public SumQueryProcessor(Client client, ChMetricTemplateEngine templateEngine) {
    this.client = client;
    this.templateEngine = templateEngine;
  }

  private record SumsGroupKey(Map<String, String> tags, String unit) {}

  public GetMetricsResponse getSumRes(GetMetricsRequest query) {
    var metric = query.getMetric();
    var tags = query.getTags();
    var ts = query.getStart();
    var te = query.getEnd();
    var temporality =
        query.getSumsQueryConfig() == null
            ? GetSumsQueryConfig.TEMPORALITY.DELTA_AGGREGATE
            : query.getSumsQueryConfig().getTemporality();
    var sumType =
        switch (temporality) {
          case CUMULATIVE -> CH_SUM_TYPE.CUMULATIVE;
          case DELTA_AGGREGATE -> CH_SUM_TYPE.DELTA;
        };
    var scan = scanSumSamples(ts, te, metric, tags, sumType);
    if (scan.isEmpty()) {
      return CannedResponses.noMetricsResponse(metric, tags);
    }

    var grouped = ArrayListMultimap.<SumsGroupKey, ChSumSample>create();
    List<Sum> sums = new ArrayList<>();
    scan.forEach(
        sample -> {
          Map<String, String> sortedTags =
              sample.tags() == null ? Map.of() : new TreeMap<>(sample.tags());
          var key = new SumsGroupKey(sortedTags, sample.unit());
          grouped.put(key, sample);
        });
    for (var key : grouped.keySet()) {
      var group = grouped.get(key);
      switch (temporality) {
        case CUMULATIVE -> {
          var maxSample =
              group.stream().max(Comparator.comparingDouble(ChSumSample::value)).orElse(null);
          sums.add(
              Sum.builder()
                  .ts(maxSample.tsStart())
                  .te(maxSample.tsEnd())
                  .unit(key.unit())
                  .count(maxSample.value())
                  .build());
        }
        case DELTA_AGGREGATE -> {
          double total =
              group.stream()
                  .filter(s -> s.sumType() == CH_SUM_TYPE.DELTA)
                  .mapToDouble(ChSumSample::value)
                  .sum();
          long aggTsStart = group.stream().mapToLong(ChSumSample::tsStart).min().orElse(ts);
          long aggTsEnd = group.stream().mapToLong(ChSumSample::tsEnd).max().orElse(te);
          sums.add(Sum.builder().ts(aggTsStart).te(aggTsEnd).unit(key.unit()).count(total).build());
        }
      }
    }

    return GetMetricsResponse.builder()
        .metric(metric)
        .tags(tags)
        .sumsResponse(GetSumsResponse.builder().sums(sums).build())
        .build();
  }

  public String chScanSumsQuery(
      long ts, long te, String metric, Map<String, String> tags, CH_SUM_TYPE sumType) {

    var template =
        ChGetSumQueryTemplate.builder()
            .table(ChConstants.TBL_SUM)
            .metric(metric)
            .tags(tags)
            .ts(ts)
            .te(te)
            .sumsType(sumType.name())
            .build();
    TemplateOutput output = new StringOutput();
    templateEngine.render(ChTemplateFiles.GET_SUM_SAMPLES, template, output);
    return output.toString();
  }

  private List<ChSumSample> scanSumSamples(
      long ts, long te, String metric, Map<String, String> tags, CH_SUM_TYPE sumType) {

    var query = chScanSumsQuery(ts, te, metric, tags, sumType);
    List<GenericRecord> records = client.queryAll(query);
    var samples = new ArrayList<ChSumSample>(records.size());
    for (var record : records) {
      @SuppressWarnings("unchecked")
      var recordTags = (Map<String, String>) record.getObject("tags");
      samples.add(
          new ChSumSample(
              record.getLong("ts_start_ms"),
              record.getLong("ts_end_ms"),
              record.getDouble("value"),
              CH_SUM_TYPE.valueOf(record.getString("sums_type")),
              recordTags,
              record.getString("unit")));
    }
    return samples;
  }
}

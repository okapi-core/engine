/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.metrics.ch;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.query.GenericRecord;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.primitives.Floats;
import com.google.common.primitives.Longs;
import gg.jte.TemplateOutput;
import gg.jte.output.StringOutput;
import org.okapi.ch.ChTemplateFiles;
import org.okapi.ds.HistogramMerger;
import org.okapi.ds.TwoAtATimeMerger;
import org.okapi.metrics.ch.template.ChGetHistoQueryTemplate;
import org.okapi.metrics.ch.template.ChMetricTemplateEngine;
import org.okapi.rest.metrics.query.*;

import java.util.*;

/** Handles histogram query execution and aggregation. */
public class HistogramQueryProcessor {
  private final Client client;
  private final ChMetricTemplateEngine templateEngine;

  public HistogramQueryProcessor(Client client, ChMetricTemplateEngine templateEngine) {
    this.client = client;
    this.templateEngine = templateEngine;
  }

  public GetMetricsResponse getHistoRes(GetMetricsRequest query) {
    var ts = query.getStart();
    var te = query.getEnd();
    var histoType =
        switch (query.getHistoQueryConfig().getTemporality()) {
          case DELTA -> ChHistoSample.HISTO_TYPE.DELTA;
          case CUMULATIVE -> ChHistoSample.HISTO_TYPE.CUMULATIVE;
          case MERGED -> ChHistoSample.HISTO_TYPE.DELTA;
        };
    var readings = scanSamples(ts, te, query.getMetric(), query.getTags(), histoType);
    if(readings.isEmpty()){
      return CannedResponses.noMetricsResponse(query.getMetric(), query.getTags());
    }
    var series = buildSeries(readings, query, histoType);
    var histo = GetHistogramResponse.builder().series(series).build();
    return GetMetricsResponse.builder()
        .metric(query.getMetric())
        .tags(query.getTags())
        .histogramResponse(histo)
        .build();
  }

  private List<HistogramSeries> buildSeries(
      List<ChHistoSample> readings, GetMetricsRequest query, ChHistoSample.HISTO_TYPE histoType) {
    var byTags = ArrayListMultimap.<HistogramKey, ChHistoSample>create();
    for (var sample : readings) {
      var tagsKey =
          sample.getTags() == null ? Map.<String, String>of() : new TreeMap<>(sample.getTags());
      var histoKey = new HistogramKey(sample.getUnit(), tagsKey);
      byTags.put(histoKey, sample);
    }
    var series = new ArrayList<HistogramSeries>();
    for (var keys : byTags.keySet()) {
      var tags = keys.tags();
      var samples = byTags.get(keys);
      if (query.getHistoQueryConfig().getTemporality() == HistoQueryConfig.TEMPORALITY.CUMULATIVE) {
        var largestSampleSize = samples.stream().map(r -> r.count).max(Long::compare).orElse(0L);
        var largestSample =
            samples.stream().filter(f -> Objects.equals(f.count, largestSampleSize)).findFirst();
        largestSample.ifPresent(
            chHistoSample ->
                series.add(
                    HistogramSeries.builder()
                        .unit(keys.unit())
                        .tags(tags)
                        .histogram(toHistogram(chHistoSample))
                        .build()));
      } else if (query.getHistoQueryConfig().getTemporality() == HistoQueryConfig.TEMPORALITY.DELTA
          || query.getHistoQueryConfig().getTemporality() == HistoQueryConfig.TEMPORALITY.MERGED) {
        var deltas =
            samples.stream()
                .filter(sample -> sample.histoType == ChHistoSample.HISTO_TYPE.DELTA)
                .toList();
        var merged =
            TwoAtATimeMerger.merge(
                deltas,
                (a, b) -> {
                  var distA = new HistogramMerger.Distribution(a.getBuckets(), a.getCounts());
                  var disB = new HistogramMerger.Distribution(b.getBuckets(), b.getCounts());
                  var resampled = HistogramMerger.merge(distA, disB);
                  var sum = a.getSum() + b.getSum();
                  var count = a.getCount() + b.getCount();
                  var tsStart = Math.min(a.tsStart, b.tsStart);
                  var tsEnd = Math.max(a.tsEnd, b.tsEnd);
                  var min = Math.min(a.min, b.min);
                  var max = Math.max(a.max, b.max);
                  return ChHistoSample.builder()
                      .metric(query.getMetric())
                      .unit(keys.unit())
                      .tags(tags)
                      .histoType(histoType)
                      .sum(sum)
                      .min(min)
                      .max(max)
                      .count(count)
                      .tsStart(tsStart)
                      .tsEnd(tsEnd)
                      .counts(resampled.counts())
                      .buckets(resampled.buckets())
                      .build();
                });
        merged.ifPresent(
            sample ->
                series.add(
                    HistogramSeries.builder()
                        .unit(keys.unit())
                        .tags(tags)
                        .histogram(toHistogram(sample))
                        .build()));
      }
    }
    return series;
  }

  private record HistogramKey(String unit, Map<String, String> tags){}

  private static Histogram toHistogram(ChHistoSample chHistoSample) {
    return Histogram.builder()
        .start(chHistoSample.getTsStart())
        .end(chHistoSample.getTsEnd())
        .count(chHistoSample.getCount())
        .sum(chHistoSample.getSum())
        .counts(
            chHistoSample.getCounts() == null ? List.of() : Longs.asList(chHistoSample.getCounts()))
        .buckets(
            chHistoSample.getBuckets() == null
                ? List.of()
                : Floats.asList(chHistoSample.getBuckets()))
        .build();
  }

  public List<ChHistoSample> scanSamples(
      long ts,
      long te,
      String metrics,
      Map<String, String> tags,
      ChHistoSample.HISTO_TYPE histoType) {
    var query =
        renderHistoQuery(
            ChGetHistoQueryTemplate.builder()
                .table(ChConstants.TBL_HISTOS)
                .metric(metrics)
                .tags(tags)
                .histoType(histoType.name())
                .ts(ts)
                .te(te)
                .build());

    List<GenericRecord> records = client.queryAll(query);
    var samples = new ArrayList<ChHistoSample>(records.size());
    for (var record : records) {
      float[] buckets;
      long[] counts;
      try {
        buckets = record.getFloatArray("buckets");
      } catch (Exception e) {
        var list = record.getList("buckets");
        if (list == null) {
          buckets = null;
        } else {
          buckets = new float[list.size()];
          for (int i = 0; i < list.size(); i++) {
            buckets[i] = ((Number) list.get(i)).floatValue();
          }
        }
      }
      try {
        counts = record.getLongArray("counts");
      } catch (Exception e) {
        var list = record.getList("counts");
        counts =
            list == null ? null : list.stream().mapToLong(o -> ((Number) o).longValue()).toArray();
      }
      long totalCount = 0L;
      if (counts != null) {
        for (long c : counts) {
          totalCount += c;
        }
      }

      float min = 0f;
      float max = 0f;
      if (buckets != null && buckets.length > 0) {
        min = buckets[0];
        max = buckets[buckets.length - 1];
      }

      @SuppressWarnings("unchecked")
      var recordTags = (Map<String, String>) record.getObject("tags");
      var unit = record.getString("unit");
      var histoTypeStr =
          record.hasValue("histo_type") ? record.getString("histo_type") : histoType.name();

      samples.add(
          ChHistoSample.builder()
              .metric(record.getString("metric_name"))
              .unit(unit)
              .tags(recordTags)
              .tsStart(record.getLong("ts_start_ms"))
              .tsEnd(record.getLong("ts_end_ms"))
              .buckets(buckets)
              .counts(counts)
              .count(totalCount)
              .min(min)
              .max(max)
              .histoType(ChHistoSample.HISTO_TYPE.valueOf(histoTypeStr))
              .build());
    }

    return samples;
  }

  private String renderHistoQuery(ChGetHistoQueryTemplate data) {
    TemplateOutput output = new StringOutput();
    templateEngine.render(ChTemplateFiles.GET_HISTO_SAMPLES, data, output);
    return output.toString();
  }
}

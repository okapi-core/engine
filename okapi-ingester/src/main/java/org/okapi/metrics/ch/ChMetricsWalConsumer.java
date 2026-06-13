/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.metrics.ch;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.gson.Gson;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.okapi.rest.metrics.Exemplar;
import org.okapi.rest.metrics.ExportMetricsRequest;
import org.okapi.rest.metrics.query.METRIC_TYPE;
import org.okapi.wal.frame.WalEntry;
import org.okapi.wal.io.WalReader;
import org.okapi.wal.manager.WalManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;

@Slf4j
@RequiredArgsConstructor
public class ChMetricsWalConsumer {
  final WalReader walReader;
  final int batchSize;
  final ChWriter chWriter;
  final WalManager walManager;
  Gson gson = new Gson();

  public ChMetricsWalConsumer(int batchSize, ChWriter chWriter, ChWalResources resources) {
    this.walReader = resources.getReader();
    this.batchSize = batchSize;
    this.chWriter = chWriter;
    this.walManager = resources.getManager();
  }

  public record ChWriteWork(String mainTable, List<String> rows, List<String> meta) {}

  public ChWriteWork getGaugeSamples(ExportMetricsRequest req) {
    List<String> gaugeSamples = new ArrayList<>();
    List<String> metaRows = new ArrayList<>();
    if (req.getGauge() != null) {
      for (int i = 0; i < req.getGauge().getTs().size(); i++) {
        var ts = req.getGauge().getTs().get(i);
        var sample =
            ChGaugeSampleRow.builder()
                .metric(req.getMetricName())
                .tags(req.getTags())
                .unit(req.getUnit())
                .timestamp(ts)
                .value(req.getGauge().getValue().get(i).doubleValue())
                .build();
        gaugeSamples.add(gson.toJson(sample));
        metaRows.add(metricMetadataRow(req, METRIC_TYPE.GAUGE, null, ts, ts));
      }
    }
    return new ChWriteWork(ChConstants.TBL_GAUGES, gaugeSamples, metaRows);
  }

  public ChWriteWork getHistoSamples(ExportMetricsRequest req) {
    List<String> histoSamples = new ArrayList<>();
    List<String> metaRows = new ArrayList<>();
    if (req.getHisto() != null) {
      for (int i = 0; i < req.getHisto().getHistoPoints().size(); i++) {
        var pt = req.getHisto().getHistoPoints().get(i);
        var buckets = pt.getBuckets();
        var counts = pt.getBucketCounts();
        ChHistoSample.HISTO_TYPE histoType =
            switch (pt.getTemporality()) {
              case DELTA -> ChHistoSample.HISTO_TYPE.DELTA;
              case CUMULATIVE -> ChHistoSample.HISTO_TYPE.CUMULATIVE;
            };
        // compute min/max from buckets when available
        float min = 0f;
        float max = 0f;
        if (buckets != null && buckets.length > 0) {
          min = buckets[0];
          max = buckets[buckets.length - 1];
        }
        var sample =
            ChHistoSample.builder()
                .histoType(histoType)
                .metric(req.getMetricName())
                .tags(req.getTags())
                .tsStart(pt.getStart())
                .tsEnd(pt.getEnd())
                .unit(req.getUnit())
                .min(min)
                .max(max)
                .buckets(buckets)
                .counts(counts)
                .sum(pt.getSum())
                .count(pt.getCount())
                .build();
        histoSamples.add(gson.toJson(sample));
        metaRows.add(
            metricMetadataRow(
                req, METRIC_TYPE.HISTO, pt.getTemporality().name(), pt.getStart(), pt.getEnd()));
      }
    }
    return new ChWriteWork(ChConstants.TBL_HISTOS, histoSamples, metaRows);
  }

  public ChWriteWork getSumSamples(ExportMetricsRequest req) {
    List<String> sumSamples = new ArrayList<>();
    List<String> meta = new ArrayList<>();
    if (req.getSum() != null) {
      for (int i = 0; i < req.getSum().getSumPoints().size(); i++) {
        var pt = req.getSum().getSumPoints().get(i);
        CH_SUM_TYPE sumType =
            switch (req.getSum().getTemporality()) {
              case DELTA -> CH_SUM_TYPE.DELTA;
              case CUMULATIVE -> CH_SUM_TYPE.CUMULATIVE;
            };
        var sample =
            ChSumSampleRow.builder()
                .metricName(req.getMetricName())
                .tags(req.getTags())
                .tsStart(pt.getStart())
                .tsEnd(pt.getEnd())
                .unit(req.getUnit())
                .value(pt.getSum())
                .sumType(sumType)
                .build();
        sumSamples.add(gson.toJson(sample));
        meta.add(
            metricMetadataRow(
                req,
                METRIC_TYPE.SUM,
                req.getSum().getTemporality().name(),
                pt.getStart(),
                pt.getEnd()));
      }
    }
    return new ChWriteWork(ChConstants.TBL_SUM, sumSamples, meta);
  }

  public ChWriteWork getExponentialHistoSamples(ExportMetricsRequest req) {
    List<String> samples = new ArrayList<>();
    List<String> metaRows = new ArrayList<>();
    if (req.getExponentialHisto() != null) {
      for (var point : req.getExponentialHisto().getHistoPoints()) {
        var histoType =
            switch (point.getTemporality()) {
              case DELTA -> ChHistoSample.HISTO_TYPE.DELTA;
              case CUMULATIVE -> ChHistoSample.HISTO_TYPE.CUMULATIVE;
            };
        var sample =
            ChExponentialHistoSampleRow.builder()
                .metric(req.getMetricName())
                .tags(req.getTags())
                .histoType(histoType)
                .tsStart(point.getStart())
                .tsEnd(point.getEnd())
                .scale(point.getScale())
                .zeroThreshold(point.getZeroThreshold())
                .zeroCount(point.getZeroCount())
                .positiveOffset(point.getPositiveOffset())
                .positiveCounts(point.getPositiveCounts())
                .negativeOffset(point.getNegativeOffset())
                .negativeCounts(point.getNegativeCounts())
                .sum(point.getSum())
                .count(point.getCount())
                .unit(req.getUnit())
                .build();
        samples.add(gson.toJson(sample));
        metaRows.add(
            metricMetadataRow(
                req,
                METRIC_TYPE.HISTO,
                point.getTemporality().name(),
                point.getStart(),
                point.getEnd()));
      }
    }
    return new ChWriteWork(ChConstants.TBL_EXPONENTIAL_HISTOS, samples, metaRows);
  }

  private String metricMetadataRow(
      ExportMetricsRequest req, METRIC_TYPE eventType, String temporality, long start, long end) {
    var map = new HashMap<String, Object>();
    map.put("event_type", eventType.name());
    map.put("metric", req.getMetricName());
    map.put("tags", req.getTags());
    map.put("unit", req.getUnit());
    if (temporality != null) map.put("temporality", temporality);
    map.put("ts_start", start);
    map.put("ts_end", end);
    return gson.toJson(map);
  }

  public ChExemplarRow exemplarToChRow(Exemplar exemplar) {
    var jsonAttribs = gson.toJson(exemplar.getKv());
    var builder =
        ChExemplarRow.builder()
            .metricName(exemplar.getMetric())
            .tags(exemplar.getTags())
            .tsNanos(exemplar.getTsNanos())
            .traceId(exemplar.getTraceId())
            .spanId(exemplar.getSpanId())
            .attributesKvListJson(jsonAttribs);
    if (exemplar.getMeasurement() != null) {
      builder.doubleValue(exemplar.getMeasurement().getADouble());
      builder.intValue(exemplar.getMeasurement().getAnInteger());
    }
    return builder.build();
  }

  public List<ChExemplarRow> reqToExemplars(List<Exemplar> rows) {
    return rows.stream().map(this::exemplarToChRow).toList();
  }

  public List<ChExemplarRow> reqToExemplars(ExportMetricsRequest request) {
    var rows = new ArrayList<ChExemplarRow>();
    if (request.getGauge() != null && request.getGauge().getExemplars() != null) {
      rows.addAll(reqToExemplars(request.getGauge().getExemplars()));
    }
    if (request.getHisto() != null && request.getHisto().getExemplars() != null) {
      rows.addAll(reqToExemplars(request.getHisto().getExemplars()));
    }
    return rows;
  }

  public ChWriteWork exemplarWriteWork(ExportMetricsRequest request) {
    var exemplars = reqToExemplars(request).stream().map(gson::toJson).toList();
    return new ChWriteWork(ChConstants.TBL_EXEMPLAR, exemplars, Collections.emptyList());
  }

  public void consumeRecords() throws IOException, InterruptedException, ExecutionException {
    // todo: use a MetricEventSupplier -> pull based model to get MetricEvents, keep the rest of the
    // logic the same.
    // todo: replace WalReader with MetricEvent interface with methods: String payload(), lsn()
    // todo: this interface should have served from either Wal or KafkaStreamPayload
    // todo: add a EventLsn  interface with implementations for Lsn, KafkaLsn (this should be a
    // Kafka offset)
    // todo: MetricEventSupplier should supply a List<MetricEvent>
    // todo: add another MetricEventCommitter interface -> this should have a commit(EventLsn lsn).
    // Two implementations: one of Kafka and one for Disk.
    var batch = walReader.readBatchAndAdvance(batchSize);

    Multimap<String, String> writeLoad = ArrayListMultimap.create();

    for (var entry : batch) {
      var req = gson.fromJson(new String(entry.getPayload()), ExportMetricsRequest.class);
      var gaugeWrites = getGaugeSamples(req);
      writeLoad.putAll(gaugeWrites.mainTable(), gaugeWrites.rows());

      var histoWrites = getHistoSamples(req);
      writeLoad.putAll(histoWrites.mainTable(), histoWrites.rows());

      var sumWrites = getSumSamples(req);
      writeLoad.putAll(sumWrites.mainTable(), sumWrites.rows());

      var exponentialHistoWrites = getExponentialHistoSamples(req);
      writeLoad.putAll(exponentialHistoWrites.mainTable(), exponentialHistoWrites.rows());

      writeLoad.putAll(ChConstants.TBL_METRIC_EVENTS_META, gaugeWrites.meta());
      writeLoad.putAll(ChConstants.TBL_METRIC_EVENTS_META, histoWrites.meta());
      writeLoad.putAll(ChConstants.TBL_METRIC_EVENTS_META, sumWrites.meta());
      writeLoad.putAll(ChConstants.TBL_METRIC_EVENTS_META, exponentialHistoWrites.meta());
      var exemplarRows = exemplarWriteWork(req);
      writeLoad.putAll(ChConstants.TBL_EXEMPLAR, exemplarRows.rows());
    }

    chWriter.writeSyncWithBestEffort(writeLoad);
    var largestLsn = WalEntry.getMaxLsn(batch);
    walManager.commitLsn(largestLsn);
  }
}

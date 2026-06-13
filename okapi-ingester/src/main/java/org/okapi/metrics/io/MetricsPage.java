/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.metrics.io;

import org.okapi.logs.io.AbstractTimestampedPage;
import org.okapi.metrics.common.MetricPaths;
import org.okapi.pages.AbstractTimeBlockMetadata;
import org.okapi.pages.AppendOnlyPage;
import org.okapi.primitives.Histogram;
import org.okapi.primitives.ReadOnlySketch;
import org.okapi.primitives.ReadonlyHistogram;
import org.okapi.rest.metrics.ExportMetricsRequest;
import org.okapi.rest.metrics.payloads.*;
import org.okapi.wal.lsn.Lsn;

import java.util.Optional;

public class MetricsPage extends AbstractTimestampedPage
    implements AppendOnlyPage<
        ExportMetricsRequest, MetricsPageSnapshot, MetricsPageMetadata, MetricsPageBody> {

  long maxTimeRangeMs;
  long maxSizeBytes;
  MetricsPageMetadata metadata;
  MetricsPageBody body;

  public MetricsPage(
      MetricsPageMetadata metadata, MetricsPageBody body, long maxTimeRangeMs, long maxSizeBytes) {
    this.metadata = metadata;
    this.body = body;
    this.maxTimeRangeMs = maxTimeRangeMs;
    this.maxSizeBytes = maxSizeBytes;
  }

  public MetricsPage(MetricsPageMetadata metadata, MetricsPageBody body) {
    this(metadata, body, 0, 0);
  }

  public Optional<ReadOnlySketch> getSecondly(String path, Long ts, double[] ranks) {
    return body.getSecondly(path, ts, ranks);
  }

  public Optional<ReadonlyHistogram> getHistogram(String path, Long ts) {
    return body.getHistogram(path, ts);
  }

  public MetricsPage(long maxTimeRangeMs, long maxSizeBytes, int maxInsertions, double fpp) {
    this(
        new MetricsPageMetadata(maxInsertions, fpp),
        new MetricsPageBody(),
        maxTimeRangeMs,
        maxSizeBytes);
  }

  @Override
  public void append(ExportMetricsRequest request) {
    var path = MetricPaths.localPath(request.getMetricName(), request.getTags());
    metadata.addPathMetadata(request.getMetricName());
    metadata.addTagPatternMetadata(request.getTags());
    if (request.getGauge() != null) {
      processGauge(path, request.getGauge());
    }
    if (request.getHisto() != null) {
      processHistogram(path, request.getHisto());
    }
    if (request.getSum() != null) {
      processSum(path, request.getSum());
    }
  }

  public void processGauge(String path, Gauge gauge) {
    for (int i = 0; i < gauge.getTs().size(); i++) {
      var ts = gauge.getTs().get(i);
      metadata.updateTsStart(ts);
      metadata.updateTsEnd(ts);
      body.updateGauge(path, gauge.getTs().get(i), gauge.getValue().get(i));
    }
  }

  public void processHistogram(String path, Histo histo) {
    for (int i = 0; i < histo.getHistoPoints().size(); i++) {
      processHistogram(path, histo.getHistoPoints().get(i));
    }
  }

  public void processHistogram(String path, HistoPoint histo) {
    metadata.updateTsStart(histo.getStart());
    metadata.updateTsEnd(histo.getEnd());
    var temporality =
        switch (histo.getTemporality()) {
          case DELTA -> Histogram.TEMPORALITY.DELTA;
          case CUMULATIVE -> Histogram.TEMPORALITY.CUMULATIVE;
        };
    var histogram =
        new Histogram(
            histo.getStart(),
            histo.getEnd(),
            temporality,
            histo.getBucketCounts(),
            histo.getBuckets());
    body.updateHistogram(path, histogram.getStartTs(), histogram);
  }

  public void processSum(String path, Sum sum) {
    for (int i = 0; i < sum.getSumPoints().size(); i++) {
      var pt = sum.getSumPoints().get(i);
      processSumPoints(path, pt);
    }
  }

  public void processSumPoints(String path, SumPoint sumPoint) {
    // convert into a single bucket histogram
    metadata.updateTsStart(sumPoint.getStart());
    metadata.updateTsEnd(sumPoint.getEnd());
    var histogram =
        new Histogram(
            sumPoint.getStart(),
            sumPoint.getEnd(),
            Histogram.TEMPORALITY.CUMULATIVE,
            new long[] {sumPoint.getSum()},
            new float[] {});
    body.updateHistogram(path, sumPoint.getStart(), histogram);
  }

  @Override
  public AbstractTimeBlockMetadata getBlockMetadata() {
    return this.getMetadata();
  }

  @Override
  public boolean isFull() {
    return this.body.getApproximateSize() >= maxSizeBytes;
  }

  @Override
  public boolean isEmpty() {
    return this.metadata.getTsStart() == 0 && this.metadata.getTsEnd() == 0;
  }

  @Override
  public MetricsPageSnapshot snapshot() {
    return new MetricsPageSnapshot(this.metadata.toSnapshot(), this.body.toSnapshot());
  }

  @Override
  public MetricsPageMetadata getMetadata() {
    return metadata;
  }

  @Override
  public MetricsPageBody getPageBody() {
    return body;
  }

  @Override
  public Lsn getMaxLsn() {
    return Lsn.fromNumber(this.metadata.getMaxLsn());
  }

  @Override
  public void updateLsn(Lsn lsn) {
    this.metadata.setMaxLsn(lsn.getNumber());
  }
}

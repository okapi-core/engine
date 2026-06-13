/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.eval;

import java.util.Map;
import org.okapi.metrics.pojos.results.Scan;

public class VectorData {
  public record Labels(Map<String, String> tags) {}

  public record SeriesId(String metric, Labels labels, boolean dropMetricName) {
    public SeriesId(String metric, Labels labels) {
      this(metric, labels, false);
    }
  }

  public record Sample(long ts, long sourceTs, double value, HistogramSeries.HistogramSample histogram) {
    public Sample(long ts, double value) {
      this(ts, ts, value, null);
    }

    public Sample(long ts, long sourceTs, double value) {
      this(ts, sourceTs, value, null);
    }

    public Sample(long ts, long sourceTs, HistogramSeries.HistogramSample histogram) {
      this(ts, sourceTs, Double.NaN, histogram);
    }

    public boolean isHistogram() {
      return histogram != null;
    }
  }

  public record SeriesSample(SeriesId id, Sample sample) {
    public SeriesId series() {
      return id;
    }
  }

  // A window now carries the scan for the series over the requested range
  public record SeriesWindow(SeriesId id, Scan scan) {}
}

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

  public record Sample(long ts, long sourceTs, float value) {
    public Sample(long ts, float value) {
      this(ts, ts, value);
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

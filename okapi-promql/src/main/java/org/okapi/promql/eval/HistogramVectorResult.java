/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.eval;

import java.util.List;
import java.util.Objects;
import org.okapi.promql.eval.VectorData.SeriesId;

public final class HistogramVectorResult implements ExpressionResult {
  public record HistogramValue(double count, double sum) {}

  public record HistogramSample(long ts, HistogramValue value) {}

  public record SeriesHistogramSample(SeriesId id, HistogramSample sample) {}

  private final List<SeriesHistogramSample> data;

  public HistogramVectorResult(List<SeriesHistogramSample> data) {
    this.data = Objects.requireNonNull(data, "data");
  }

  public List<SeriesHistogramSample> data() {
    return data;
  }

  @Override
  public ValueType type() {
    return ValueType.INSTANT_VECTOR;
  }
}

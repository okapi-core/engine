/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.metrics.service;

import org.okapi.primitives.ReadonlyHistogram;
import org.okapi.rest.metrics.query.GetHistogramResponse;
import org.okapi.rest.metrics.query.Histogram;
import org.okapi.rest.metrics.query.HistogramSeries;

import java.util.List;

public class HistoAggregator {
  public static final GetHistogramResponse aggregateHistograms(List<ReadonlyHistogram> histograms) {
    var histos =
        histograms.stream()
            .map(
                h ->
                    Histogram.builder()
                        .buckets(h.getBuckets())
                        .counts(h.getBucketCounts())
                        .start(h.getStartTs())
                        .end(h.getEndTs())
                        .build())
            .map((histo) -> HistogramSeries.builder().tags(null).histogram(histo).build())
            .toList();
    return GetHistogramResponse.builder().series(histos).build();
  }
}

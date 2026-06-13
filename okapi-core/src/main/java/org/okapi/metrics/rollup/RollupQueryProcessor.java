/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.metrics.rollup;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.function.BiFunction;
import org.okapi.metrics.pojos.RES_TYPE;
import org.okapi.metrics.query.QueryProcessor;
import org.okapi.metrics.query.QueryRecords;

public class RollupQueryProcessor implements QueryProcessor {
  private static long getIncrement(RES_TYPE resType) {
    return switch (resType) {
      case SECONDLY -> 1000L;
      case MINUTELY -> 60 * 1000L;
      case HOURLY -> 60 * 60 * 1000L;
    };
  }

  private static long discretizeAndBack(long ts, RES_TYPE resType) {
    var inc = getIncrement(resType);
    return inc * (ts / inc);
  }

  @Override
  public QueryRecords.QueryResult scan(TsReader rollupSeries, QueryRecords.Slice slice) {
    var scanResult =
        rollupSeries.scanGauge(
            slice.series(), slice.from(), slice.to(), slice.aggregation(), slice.resolution());
    return new QueryRecords.QueryResult(
        scanResult.getUniversalPath(), scanResult.getTimestamps(), scanResult.getValues());
  }

  @Override
  public QueryRecords.QueryResult scale(
      TsReader rollupSeries, QueryRecords.Slice slice, float scaleFactor) {
    var scaledResult =
        rollupSeries.scanGauge(
            slice.series(), slice.from(), slice.to(), slice.aggregation(), slice.resolution());
    var ts = scaledResult.getTimestamps();
    var values = scaledResult.getValues();
    values.replaceAll(aFloat -> aFloat * scaleFactor);
    return new QueryRecords.QueryResult(scaledResult.getUniversalPath(), ts, values);
  }

  @Override
  public QueryRecords.QueryResult sum(
      TsReader rollupSeries, QueryRecords.Slice left, QueryRecords.Slice right) {
    var scanLeft =
        rollupSeries.scanGauge(
            left.series(), left.from(), left.to(), left.aggregation(), left.resolution());
    var scanRight =
        rollupSeries.scanGauge(
            right.series(), right.from(), right.to(), right.aggregation(), right.resolution());
    int nLeft = scanLeft.getValues().size();
    int nRight = scanRight.getValues().size();
    int i = 0, j = 0;
    var sums = new ArrayList<Float>(Math.max(nLeft, nRight));
    var timestamps = new ArrayList<Long>(Math.max(nLeft, nRight));
    while (i < nLeft && j < nRight) {
      long leftTs = scanLeft.getTimestamps().get(i);
      long rightTs = scanRight.getTimestamps().get(j);
      if (leftTs < rightTs) {
        sums.add(scanLeft.getValues().get(i));
        timestamps.add(leftTs);
        i++;
      } else if (leftTs > rightTs) {
        sums.add(scanRight.getValues().get(j));
        timestamps.add(rightTs);
        j++;
      } else {
        sums.add(scanLeft.getValues().get(i) + scanRight.getValues().get(j));
        timestamps.add(leftTs);
        i++;
        j++;
      }
    }

    while (i < nLeft) {
      sums.add(scanLeft.getValues().get(i));
      timestamps.add(scanLeft.getTimestamps().get(i));
      i++;
    }

    while (j < nRight) {
      sums.add(scanRight.getValues().get(j));
      timestamps.add(scanRight.getTimestamps().get(j));
      j++;
    }

    return new QueryRecords.QueryResult(left.series() + "+" + right.series(), timestamps, sums);
  }

  @Override
  public float count(TsReader rollupSeries, QueryRecords.Slice slice) {
    throw new IllegalArgumentException();
  }

  @Override
  public QueryRecords.QueryResult transform(
      TsReader rollupSeries, QueryRecords.Slice slice, QueryProcessor.TRANSFORM transform) {
    var scaledResult =
        rollupSeries.scanGauge(
            slice.series(), slice.from(), slice.to(), slice.aggregation(), slice.resolution());
    var ts = scaledResult.getTimestamps();
    var values = scaledResult.getValues();
    values.replaceAll(
        (s) -> {
          return switch (transform) {
            case LOG -> (float) Math.log(s);
            case SIGMOID -> (float) (1 / (1 + Math.exp(-s)));
            default -> throw new IllegalArgumentException("Unknown transform: " + transform);
          };
        });
    return new QueryRecords.QueryResult(scaledResult.getUniversalPath(), ts, values);
  }

  @Override
  public QueryRecords.QueryResult movingAverage(
      TsReader rollupSeries, QueryRecords.Slice slice, Duration windowSize) {
    return movingWindowTransform(rollupSeries, slice, windowSize, (sum, count) -> sum / count);
  }

  protected QueryRecords.QueryResult movingWindowTransform(
      TsReader rollupSeries,
      QueryRecords.Slice slice,
      Duration windowSize,
      BiFunction<Float, Integer, Float> valueComputeFn) {
    var scanResult =
        rollupSeries.scanGauge(
            slice.series(), slice.from(), slice.to(), slice.aggregation(), slice.resolution());
    var timestamps = scanResult.getTimestamps();
    if (timestamps.isEmpty()) {
      return new QueryRecords.QueryResult(
          slice.series(), Collections.emptyList(), Collections.emptyList());
    }
    var values = scanResult.getValues();
    var st = discretizeAndBack(slice.from(), slice.resolution());
    var end = discretizeAndBack(slice.to(), slice.resolution());
    var inc = getIncrement(slice.resolution());
    var resultTs = new ArrayList<Long>();
    var resultVals = new ArrayList<Float>();
    // winStart and winEnd are indices in timestamps array such that they represent the largest
    // window of size <= windowSize
    // and the timestamp at winEnd is the largest timestamp that is <= ts
    var winStart = 0;
    var winEnd = 0;
    var movingSum = 0.f;
    var count = 0;
    // iterate over all discretized timestamp positions for which this value would be calculated
    //
    for (long ts = st; ts <= end; ts += inc) {
      while (winEnd < timestamps.size() && timestamps.get(winEnd) <= ts) {
        count++;
        movingSum += values.get(winEnd);
        winEnd++;
      }

      while (timestamps.get(winStart) < ts - windowSize.toMillis()) {
        count--;
        movingSum -= values.get(winStart);
        winStart++;
      }

      var finalValue = valueComputeFn.apply(movingSum, count);
      resultTs.add(ts);
      resultVals.add(finalValue);
    }

    return new QueryRecords.QueryResult(slice.series(), resultTs, resultVals);
  }

  @Override
  public QueryRecords.QueryResult movingSum(
      TsReader rollupSeries, QueryRecords.Slice slice, Duration windowSize) {
    return movingWindowTransform(rollupSeries, slice, windowSize, (sum, count) -> sum);
  }

  @Override
  public QueryRecords.QueryResult firstDerivative(TsReader rollupSeries, QueryRecords.Slice slice) {
    var scaledResult =
        rollupSeries.scanGauge(
            slice.series(), slice.from(), slice.to(), slice.aggregation(), slice.resolution());
    var ts = scaledResult.getTimestamps();
    var values = scaledResult.getValues();
    var derivatives = new ArrayList<Float>();
    var times = new ArrayList<Long>();
    for (int i = 1; i < ts.size(); i++) {
      var diff = ts.get(i) - ts.get(i - 1);
      var valDiff = values.get(i) - values.get(i - 1);
      var derivative = valDiff / diff;
      derivatives.add((float) derivative);
      times.add(ts.get(i));
    }

    return new QueryRecords.QueryResult(slice.series(), times, derivatives);
  }

  @Override
  public QueryRecords.QueryResult aggregateSum(TsReader rollupSeries, QueryRecords.Slice slice) {
    var windowSize = slice.from() - slice.to();
    return movingWindowTransform(
        rollupSeries, slice, Duration.of(windowSize, ChronoUnit.MILLIS), (sum, count) -> sum);
  }
}

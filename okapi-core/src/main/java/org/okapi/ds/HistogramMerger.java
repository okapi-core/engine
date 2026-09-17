/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.ds;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.okapi.collections.OkapiLists;

public class HistogramMerger {
  public record Distribution(float[] buckets, long[] counts) {}

  public static float[] getDistribution(long[] counts) {
    long sum = Arrays.stream(counts).sum();
    float[] f = new float[counts.length];
    f[0] = (0.f + counts[0]) / sum;
    long running = counts[0];
    for (int i = 1; i < counts.length; i++) {
      running += counts[i];
      f[i] = (0.f + running) / sum;
    }
    return f;
  }

  public static float linearInterpolate(float l, float fl, float m, float r, float fr) {
    return fl + (fr - fl) / (r - l) * (m - l);
  }

  public static Distribution merge(Distribution a, Distribution b) {
    var isIdentical = Arrays.equals(a.buckets, b.buckets);
    if (isIdentical) {
      var sum = new long[a.counts.length];
      for (int i = 0; i < a.counts.length; i++) {
        sum[i] = a.counts[i] + b.counts[i];
      }
      return new Distribution(a.buckets, sum);
    }
    var distA = getDistribution(a.counts);
    var distB = getDistribution(b.counts);
    int i = 0;
    int j = 0;
    List<Float> buckets = new ArrayList<>();
    List<Float> cdf = new ArrayList<>();
    var totalSamples = Arrays.stream(a.counts).sum() + Arrays.stream(b.counts).sum();
    while (i < a.buckets.length && j < b.buckets.length) {
      if (a.buckets[i] == b.buckets[j]) {
        var avg = (distA[i] + distB[j]) / 2.f;
        buckets.add(a.buckets[i]);
        cdf.add(avg);
        i++;
        j++;
      } else if (a.buckets[i] < b.buckets[j]) {
        var cdfa = distA[i];
        if (j == 0) {
          cdf.add(cdfa);
        } else {
          var cdfb =
              linearInterpolate(
                  b.buckets[j - 1], distB[j - 1], a.buckets[i], b.buckets[j], distB[j]);
          var avg = (cdfa + cdfb) / 2.f;
          cdf.add(avg);
        }
        buckets.add(a.buckets[i]);
        i++;
      } else {
        var cdfb = distB[j];
        if (i == 0) {
          cdf.add(cdfb);
        } else {
          var cdfa =
              linearInterpolate(
                  a.buckets[i - 1], distA[i - 1], b.buckets[j], b.buckets[j], distB[j]);
          var avg = (cdfa + cdfb) / 2.f;
          cdf.add(avg);
        }
        buckets.add(b.buckets[j]);
        j++;
      }
    }

    while (i < a.buckets.length) {
      buckets.add(a.buckets[i]);
      cdf.add(distA[i]);
      i++;
    }

    while (j < b.buckets.length) {
      buckets.add(b.buckets[j]);
      cdf.add(distB[j]);
      j++;
    }
    long[] counts = new long[1 + buckets.size()];
    counts[0] = (int) (cdf.get(0) * totalSamples);
    var leftOver = totalSamples - counts[0];
    for (int n = 1; n < buckets.size(); n++) {
      counts[n] = (int) ((cdf.get(n) - cdf.get(n - 1)) * totalSamples);
      leftOver -= counts[n];
    }
    counts[buckets.size()] = leftOver;
    return new Distribution(OkapiLists.toFloatArray(buckets), counts);
  }
}

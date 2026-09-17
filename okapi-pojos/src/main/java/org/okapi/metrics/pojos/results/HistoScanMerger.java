/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.metrics.pojos.results;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Utility to merge multiple HistoScan instances into a single histogram.
 *
 * <p>If all scans have identical finite upper bounds (ubs), counts are merged by element-wise sum.
 * Otherwise, the scans are merged approximately by constructing a combined CDF F(x) = sum_i (N_i /
 * N_total) * F_i(x), and rebinning onto a target schema which is the union of all finite bounds
 * across inputs (sorted ascending). Infinite head/tail buckets are preserved.
 */
public final class HistoScanMerger {

  private HistoScanMerger() {}

  public static HistoScan merge(String universalPath, List<HistoScan> scans) {
    if (scans == null || scans.isEmpty()) {
      return new HistoScan(universalPath, 0L, 0L, List.of(), List.of());
    }

    // Aggregate time span
    long start = Long.MAX_VALUE, end = Long.MIN_VALUE;
    for (var s : scans) {
      start = Math.min(start, s.getStart());
      end = Math.max(end, s.getEnd());
    }

    // If all ubs equal, sum counts directly
    boolean sameSchema = haveSameBounds(scans);
    if (sameSchema) {
      List<Float> ubs = scans.get(0).getUbs();
      int n = ubs.size();
      int[] sum = new int[n + 1];
      for (var s : scans) {
        var c = s.getCounts();
        int limit = Math.min(c.size(), n + 1);
        for (int i = 0; i < limit; i++) sum[i] += c.get(i);
        // If c.size() < n+1, treat missing buckets as zeros.
      }
      List<Integer> counts = new ArrayList<>(n + 1);
      for (int v : sum) counts.add(v);
      return new HistoScan(universalPath, start, end, ubs, Collections.unmodifiableList(counts));
    }

    // Build union bounds as target schema
    List<Float> targetBounds = unionBounds(scans);
    int m = targetBounds.size();
    double[] merged = new double[m + 1];
    int totalN = 0;
    double[] weights = new double[scans.size()];
    for (int i = 0; i < scans.size(); i++) {
      int n = totalCount(scans.get(i));
      weights[i] = n;
      totalN += n;
    }
    if (totalN == 0) {
      List<Integer> zeros = new ArrayList<>(Collections.nCopies(m + 1, 0));
      return new HistoScan(universalPath, start, end, targetBounds, zeros);
    }
    for (int i = 0; i < weights.length; i++) weights[i] /= totalN;

    // For each target edge, compute F(x-) and allocate counts via differences
    double[] cdfAt = new double[m];
    for (int j = 0; j < m; j++) {
      double x = targetBounds.get(j);
      double F = 0.0;
      for (int i = 0; i < scans.size(); i++) {
        F += weights[i] * cdfLeft(scans.get(i), x);
      }
      cdfAt[j] = F;
    }
    // Convert CDF to counts: head, interior, tail
    // Head: F(b0-)
    double prev = 0.0;
    for (int j = 0; j < m; j++) {
      double curr = clamp01(cdfAt[j]);
      merged[j] += (curr - prev) * totalN;
      prev = curr;
    }
    // Tail remainder
    merged[m] = Math.max(0.0, totalN - Arrays.stream(merged, 0, m).sum());

    // Round and adjust to preserve total
    int[] rounded = new int[m + 1];
    long sumInt = 0;
    for (int i = 0; i <= m; i++) {
      rounded[i] = (int) Math.round(merged[i]);
      if (rounded[i] < 0) rounded[i] = 0;
      sumInt += rounded[i];
    }
    int diff = (int) (totalN - sumInt);
    if (diff != 0) adjustCounts(rounded, diff);

    List<Integer> counts = new ArrayList<>(m + 1);
    for (int v : rounded) counts.add(v);
    return new HistoScan(
        universalPath, start, end, targetBounds, Collections.unmodifiableList(counts));
  }

  private static boolean haveSameBounds(List<HistoScan> scans) {
    var ref = scans.get(0).getUbs();
    for (var s : scans) {
      var u = s.getUbs();
      if (u.size() != ref.size()) return false;
      for (int i = 0; i < u.size(); i++) if (!u.get(i).equals(ref.get(i))) return false;
    }
    return true;
  }

  private static int totalCount(HistoScan s) {
    int n = 0;
    for (int c : s.getCounts()) n += c;
    return n;
  }

  private static List<Float> unionBounds(List<HistoScan> scans) {
    Set<Float> set = new HashSet<>();
    for (var s : scans) set.addAll(s.getUbs());
    List<Float> out = new ArrayList<>(set);
    out.sort(Comparator.naturalOrder());
    return Collections.unmodifiableList(out);
  }

  // Left-limit CDF at x (approaching from below), excluding head mass when x <= first bound.
  private static double cdfLeft(HistoScan s, double x) {
    var ubs = s.getUbs();
    var c = s.getCounts();
    int n = ubs.size();
    int N = totalCount(s);
    if (N == 0) return 0.0;
    if (n == 0) return 0.0;

    // Head bucket mass contributes only for x <= first finite bound
    if (x <= ubs.get(0)) {
      return (double) c.get(0) / N;
    }
    // Find finite bucket k such that ubs[k-1] < x <= ubs[k], or x > ubs[n-1]
    int k = 1;
    while (k < n && x > ubs.get(k)) k++;
    double sum = c.get(0); // include head
    for (int i = 1; i < k; i++) sum += c.get(i);
    if (k < n) {
      double L = ubs.get(k - 1);
      double R = ubs.get(k);
      double width = Math.max(R - L, 0.0);
      if (width > 0) {
        double frac = Math.min(1.0, Math.max(0.0, (x - L) / width));
        sum += c.get(k) * frac;
      }
      return sum / N;
    } else {
      // x > last finite bound: exclude tail mass for finite x
      for (int i = k; i < n; i++) sum += c.get(i);
      return sum / N; // tail excluded
    }
  }

  private static double clamp01(double v) {
    return v < 0 ? 0 : (v > 1 ? 1 : v);
  }

  private static void adjustCounts(int[] arr, int diff) {
    // Distribute +/-1 adjustments across buckets from the largest fractional contributions.
    int i = 0;
    int step = diff > 0 ? 1 : -1;
    diff = Math.abs(diff);
    while (diff > 0 && i < arr.length) {
      arr[i] += step;
      diff--;
      i++;
      if (i == arr.length) i = 0;
    }
    for (int j = 0; j < arr.length; j++) if (arr[j] < 0) arr[j] = 0;
  }
}

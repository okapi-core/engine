/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql;

import java.util.*;
import org.okapi.metrics.pojos.results.GaugeScan;
import org.okapi.metrics.pojos.results.HistoScan;
import org.okapi.metrics.pojos.results.Scan;
import org.okapi.promql.eval.ts.RESOLUTION;
import org.okapi.promql.eval.ts.TsClient;

public final class MockTimeSeriesClient implements TsClient {

  // Backing store: (metric,tags) -> timestamp -> float value (value means:
  //  - for gauges: the gauge value at timestamp
  //  - for counters: the count observed over the bucket starting at timestamp)
  private final Map<Key, NavigableMap<Long, Float>> store = new HashMap<>();
  private final Map<Key, HistoScan> histoStore = new HashMap<>();

  public void put(String metric, Map<String, String> tags, long ts, float value) {
    var key = new Key(metric, tags);
    var series = store.computeIfAbsent(key, k -> new TreeMap<>());
    series.put(ts, value);
  }

  public void putHisto(
      String metric,
      Map<String, String> tags,
      long startMs,
      long endMs,
      List<Float> ubs,
      List<Integer> counts) {
    var key = new Key(metric, tags);
    histoStore.put(key, new HistoScan("", startMs, endMs, List.copyOf(ubs), List.copyOf(counts)));
  }

  @Override
  public Scan get(String name, Map<String, String> tags, RESOLUTION res, long startMs, long endMs) {
    var key = new Key(name, tags == null ? Map.of() : tags);
    // Histogram metric?
    if (name != null && name.endsWith("_histo")) {
      var hs = histoStore.get(key);
      if (hs == null) {
        return new HistoScan("", startMs, endMs, List.of(), List.of());
      }
      // Return the stored histogram (ignore requested window for simplicity in tests)
      return hs;
    }
    var series = store.get(key);
    if (series == null) {
      return GaugeScan.builder().universalPath("").timestamps(List.of()).values(List.of()).build();
    }
    NavigableMap<Long, Float> sub = series.subMap(startMs, true, endMs, true);
    if (name != null && name.endsWith("_counter")) {
      // Build SumScan
      List<Long> ts = new ArrayList<>(sub.size());
      List<Double> counts = new ArrayList<>(sub.size());
      for (var e : sub.entrySet()) {
        ts.add(e.getKey());
        counts.add((double) e.getValue());
      }
      long windowSize;
      if (ts.size() >= 2) windowSize = Math.max(1L, ts.get(1) - ts.get(0));
      else windowSize = 60_000L;
      return org.okapi.metrics.pojos.results.SumScan.builder()
          .universalPath("")
          .ts(Collections.unmodifiableList(ts))
          .windowSize(windowSize)
          .counts(Collections.unmodifiableList(counts))
          .build();
    } else {
      // Build GaugeScan
      List<Long> ts = new ArrayList<>(sub.size());
      List<Float> vals = new ArrayList<>(sub.size());
      for (var e : sub.entrySet()) {
        ts.add(e.getKey());
        vals.add(e.getValue());
      }
      return GaugeScan.builder()
          .universalPath("")
          .timestamps(Collections.unmodifiableList(ts))
          .values(Collections.unmodifiableList(vals))
          .build();
    }
  }

  public static final class Key {
    public final String metric;
    public final Map<String, String> tags;

    public Key(String metric, Map<String, String> tags) {
      this.metric = metric;
      this.tags = Collections.unmodifiableMap(new HashMap<>(tags));
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof Key k)) return false;
      return Objects.equals(metric, k.metric) && Objects.equals(tags, k.tags);
    }

    @Override
    public int hashCode() {
      return Objects.hash(metric, tags);
    }
  }
}

/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.eval;

import java.util.*;
import lombok.AllArgsConstructor;
import org.okapi.promql.eval.VectorData.*;
import org.okapi.promql.eval.VectorData.SeriesSample;

@AllArgsConstructor
public final class InstantVectorResult implements ExpressionResult, Iterable<SeriesSample> {
  private final List<SeriesSample> data;

  public List<SeriesSample> data() {
    return data;
  }

  @Override
  public Iterator<SeriesSample> iterator() {
    return data.iterator();
  }

  @Override
  public ValueType type() {
    return ValueType.INSTANT_VECTOR;
  }

  public Map<SeriesId, List<Sample>> toMatrix() {
    final Map<SeriesId, List<Sample>> groups = new LinkedHashMap<>();
    final Map<SeriesId, Long> lastTs = new HashMap<>();
    boolean needSort = false;

    for (SeriesSample s : this.data) {
      final SeriesId sid = s.series();
      final Sample smp = s.sample();

      // Append sample to its group's list (create if absent)
      List<Sample> list = groups.get(sid);
      if (list == null) {
        list = new ArrayList<>();
        groups.put(sid, list);
      }

      // Detect out-of-order timestamps per series
      final Long prev = lastTs.put(sid, smp.ts());
      if (prev != null && smp.ts() < prev) {
        needSort = true;
      }

      list.add(smp);
    }

    // Sort only if we ever saw an out-of-order timestamp
    if (needSort) {
      for (List<Sample> list : groups.values()) {
        list.sort(Comparator.comparingLong(Sample::ts));
      }
    }

    // Return unmodifiable snapshot
    Map<SeriesId, List<Sample>> result = new LinkedHashMap<>(groups.size());
    for (var e : groups.entrySet()) {
      result.put(e.getKey(), Collections.unmodifiableList(new ArrayList<>(e.getValue())));
    }
    return Collections.unmodifiableMap(result);
  }
}

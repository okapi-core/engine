/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.tsvector;

import java.util.*;

public class TreeMapTimeVector implements TimeVector {
  SortedMap<Long, Float> timeValueMap;

  public TreeMapTimeVector(SortedMap<Long, Float> timeValueMap) {
    this.timeValueMap = timeValueMap;
  }

  public TreeMapTimeVector() {
    this.timeValueMap = new TreeMap<>();
  }

  @Override
  public Optional<Float> at(long timestamp) {
    return Optional.of(this.timeValueMap.get(timestamp));
  }

  @Override
  public TimeVector slice(long startTimestamp, long endTimestamp) {
    var slice =
        Collections.unmodifiableSortedMap(this.timeValueMap.subMap(startTimestamp, endTimestamp));
    return new TreeMapTimeVector(slice);
  }

  @Override
  public List<Float> values() {
    return timeValueMap.values().stream().toList();
  }

  @Override
  public List<Long> timestamps() {
    return timeValueMap.keySet().stream().toList();
  }
}

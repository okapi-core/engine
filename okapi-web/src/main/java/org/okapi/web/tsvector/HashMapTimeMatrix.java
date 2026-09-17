/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.tsvector;

import java.util.*;

public class HashMapTimeMatrix implements TimeMatrix {
  String error;
  String errorType;
  Map<String, TimeVector> map;

  public HashMapTimeMatrix(String error) {
    this.error = error;
  }

  public HashMapTimeMatrix(Map<String, TimeVector> map) {
    this.map = Collections.unmodifiableMap(map);
  }

  public HashMapTimeMatrix() {
    this.map = new HashMap<>();
  }

  @Override
  public TimeVector getTimeVector(String path) {
    return this.map.get(path);
  }

  @Override
  public int size() {
    return this.map.size();
  }

  @Override
  public List<String> getPaths() {
    return this.map.keySet().stream().toList();
  }
}

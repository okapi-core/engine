/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.metrics.common;

import java.util.Map;
import java.util.TreeMap;

public class MetricPaths {

  public static String getMetricPath(String universalMetricName, Map<String, String> unsortedTags) {
    var sb = new StringBuilder();
    var sortedMap = new TreeMap<>(unsortedTags);
    sb.append(universalMetricName);
    sb.append("{");
    var first = true;
    for (var entry : sortedMap.entrySet()) {
      if (!first) {
        sb.append(",");
      }
      sb.append(entry.getKey()).append("=").append(entry.getValue());
      first = false;
    }
    sb.append("}");
    return sb.toString();
  }
}

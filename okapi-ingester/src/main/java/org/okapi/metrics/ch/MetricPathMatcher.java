/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.metrics.ch;

import com.google.re2j.Pattern;
import java.util.HashMap;
import java.util.Map;
import org.okapi.rest.search.SearchMetricsRequest;

public class MetricPathMatcher {
  String metricValue;
  Pattern metricPattern;
  Map<String, String> labelValues;
  Map<String, Pattern> labelValuePatterns;

  public static MetricPathMatcher fromRequest(SearchMetricsRequest request) {
    var matcher = new MetricPathMatcher();
    matcher.metricValue = request.getMetricName();
    if (request.getMetricNamePattern() != null) {
      matcher.metricPattern = Pattern.compile(request.getMetricNamePattern());
    }
    if (request.getValueFilters() != null) {
      matcher.labelValues = new HashMap<>();
      for (var filter : request.getValueFilters()) {
        matcher.labelValues.put(filter.getLabel(), filter.getValue());
      }
    }
    if (request.getPatternFilters() != null) {
      matcher.labelValuePatterns = new HashMap<>();
      for (var filter : request.getPatternFilters()) {
        matcher.labelValuePatterns.put(filter.getLabel(), Pattern.compile(filter.getPattern()));
      }
    }
    return matcher;
  }

  public boolean matches(String metricName, Map<String, String> tags) {
    if (metricValue != null && !metricValue.equals(metricName)) {
      return false;
    }
    if (metricPattern != null && !metricPattern.matcher(metricName).matches()) {
      return false;
    }
    if (labelValues != null) {
      for (var entry : labelValues.entrySet()) {
        var tagValue = tags == null ? null : tags.get(entry.getKey());
        if (!entry.getValue().equals(tagValue)) {
          return false;
        }
      }
    }
    if (labelValuePatterns != null) {
      for (var entry : labelValuePatterns.entrySet()) {
        var tagValue = tags == null ? null : tags.get(entry.getKey());
        if (tagValue == null || !entry.getValue().matcher(tagValue).matches()) {
          return false;
        }
      }
    }
    return true;
  }
}

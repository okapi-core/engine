/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.metrics.ch;

import com.google.re2j.Pattern;
import java.util.Map;
import org.okapi.exceptions.BadRequestException;
import org.okapi.rest.search.AnyMetricOrValueFilter;

public class AnyMetricOrValueFilterMatcher {
  private final String value;
  private final Pattern pattern;

  private AnyMetricOrValueFilterMatcher(String value, Pattern pattern) {
    this.value = value;
    this.pattern = pattern;
  }

  public static AnyMetricOrValueFilterMatcher fromFilter(AnyMetricOrValueFilter filter) {
    if (filter == null) {
      return new AnyMetricOrValueFilterMatcher(null, null);
    }
    var value = filter.getValue();
    var patternStr = filter.getPattern();
    if ((value == null || value.isBlank()) && (patternStr == null || patternStr.isBlank())) {
      throw new BadRequestException("anyMetricOrValueFilter must set value or pattern");
    }
    var normalizedValue = (value != null && !value.isBlank()) ? value : null;
    var compiled = normalizedValue != null ? null : Pattern.compile(patternStr);
    return new AnyMetricOrValueFilterMatcher(normalizedValue, compiled);
  }

  public boolean matches(String metricName, Map<String, String> tags) {
    if (value == null && pattern == null) {
      return true;
    }
    if (value != null) {
      if (value.equals(metricName)) return true;
      if (tags != null && tags.containsValue(value)) return true;
      return false;
    }
    if (pattern.matcher(metricName).matches()) return true;
    if (tags != null) {
      for (var tagValue : tags.values()) {
        if (pattern.matcher(tagValue).matches()) return true;
      }
    }
    return false;
  }
}

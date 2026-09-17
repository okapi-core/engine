/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.metrics.common;

import java.util.*;

public class MetricsPathParser {

  public static Optional<MetricsRecord> parse(String path) {
    var startBrace = path.indexOf("{");
    if (startBrace == -1) {
      return Optional.of(new MetricsRecord(path, Collections.emptyMap()));
    }
    var endBrace = path.indexOf("}");
    var name = path.substring(0, startBrace);
    var tags = parseTags(path, startBrace, endBrace);
    return Optional.of(new MetricsRecord(name, tags));
  }

  public static Optional<MetricsRecord> parseNormal(String path) {
    var startBrace = path.indexOf("{");
    if (startBrace == -1) {
      if (path.isBlank()) {
        return Optional.empty();
      }
      return Optional.of(new MetricsRecord(path.trim(), Collections.emptyMap()));
    }
    var endBrace = path.indexOf("}");
    if (endBrace == -1) {
      return Optional.empty();
    }
    var name = path.substring(0, startBrace).trim();
    if (name.isEmpty()) {
      return Optional.empty();
    }
    var tags = parseTags(path, startBrace, endBrace);
    return Optional.of(new MetricsRecord(name, tags));
  }

  public record MetricsRecord(String name, Map<String, String> tags) {}

  private static Map<String, String> parseTags(String path, int startBrace, int endBrace) {
    var keyValues = path.substring(1 + startBrace, endBrace);
    if (keyValues.isBlank()) {
      return Collections.emptyMap();
    }
    var keyValuePairs = keyValues.split(",");
    var tags = new TreeMap<String, String>();
    for (var kvp : keyValuePairs) {
      var split = kvp.split("=");
      if (split.length != 2) continue;
      var key = split[0].trim();
      var value = normalizeTagValue(split[1].trim());
      if (!key.isEmpty()) {
        tags.put(key, value);
      }
    }
    return tags;
  }

  private static String normalizeTagValue(String value) {
    if (value.length() >= 2) {
      char first = value.charAt(0);
      char last = value.charAt(value.length() - 1);
      if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
        return value.substring(1, value.length() - 1);
      }
    }
    return value;
  }
}

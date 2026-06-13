/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.ch;

import java.util.LinkedHashMap;
import java.util.Map;

final class ChSqlEscaper {
  private ChSqlEscaper() {}

  static String escapeLiteral(String value) {
    if (value == null) return null;
    return value
        .replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
        .replace("\0", "\\0");
  }

  static Map<String, String> escapeTags(Map<String, String> tags) {
    if (tags == null) return null;
    Map<String, String> escaped = new LinkedHashMap<>();
    for (var entry : tags.entrySet()) {
      escaped.put(escapeLiteral(entry.getKey()), escapeLiteral(entry.getValue()));
    }
    return escaped;
  }
}

/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.ch;

import java.util.Map;

public class AbstractChTemplate {
  public String asMap(Map<String, String> map) {
    var sb = new StringBuilder();
    sb.append("map(");
    for (var entry : map.entrySet()) {
      sb.append('\'')
          .append(entry.getKey())
          .append('\'')
          .append(',')
          .append('\'')
          .append(entry.getValue())
          .append('\'')
          .append(",");
    }
    sb.setLength(sb.length() - 1);
    sb.trimToSize();
    sb.append(")");
    return sb.toString();
  }
}

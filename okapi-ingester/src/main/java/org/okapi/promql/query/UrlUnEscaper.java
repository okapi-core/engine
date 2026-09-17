/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.query;

public class UrlUnEscaper {

  public String unescape(String label) {
    if (label == null) return null;

    if (!label.startsWith("U__")) {
      return label;
    }

    String encoded = label.substring(3); // MUST be 3
    StringBuilder out = new StringBuilder();

    for (int i = 0; i < encoded.length(); i++) {
      char c = encoded.charAt(i);

      if (c == '_' && i + 2 < encoded.length()) {
        String hex = encoded.substring(i + 1, i + 3);
        if (hex.matches("[0-9a-fA-F]{2}")) {
          out.append((char) Integer.parseInt(hex, 16));
          i += 2;
          if (i + 1 < encoded.length() && encoded.charAt(i + 1) == '_') {
            i += 1;
          }
          continue;
        }
      }

      out.append(c);
    }

    return out.toString();
  }
}

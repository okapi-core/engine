/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.ql.ast;

import java.util.List;
import java.util.Objects;

public sealed interface FieldRef permits PathFieldRef, MapFieldRef {
  List<String> getPath();

  private static List<String> normalizePath(List<String> path) {
    Objects.requireNonNull(path, "path");
    if (path.isEmpty()) {
      throw new IllegalArgumentException("field path must not be empty");
    }
    return path;
  }

  static List<String> normalize(List<String> path) {
    return normalizePath(path);
  }
}

/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.ql.ast;

import java.util.List;
import java.util.Objects;
import lombok.Value;

@Value
public final class MapFieldRef implements FieldRef {
  List<String> path;
  String key;

  public MapFieldRef(List<String> path, String key) {
    this.path = FieldRef.normalize(path);
    this.key = Objects.requireNonNull(key, "key");
  }
}

/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.ql.ast;

import java.util.List;
import lombok.Value;

@Value
public final class PathFieldRef implements FieldRef {
  List<String> path;

  public PathFieldRef(List<String> path) {
    this.path = FieldRef.normalize(path);
  }
}

/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.ql.ast;

import java.util.Objects;
import lombok.Value;

@Value
public final class SortOp implements PipelineOp {
  FieldRef field;
  Direction direction;

  public SortOp(FieldRef field, Direction direction) {
    this.field = Objects.requireNonNull(field, "field");
    this.direction = Objects.requireNonNull(direction, "direction");
  }
}

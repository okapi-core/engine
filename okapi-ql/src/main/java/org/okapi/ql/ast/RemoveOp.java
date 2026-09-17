/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.ql.ast;

import java.util.List;
import lombok.Value;

@Value
public final class RemoveOp implements PipelineOp {
  List<FieldRef> fields;

  public RemoveOp(List<FieldRef> fields) {
    this.fields = fields;
  }
}

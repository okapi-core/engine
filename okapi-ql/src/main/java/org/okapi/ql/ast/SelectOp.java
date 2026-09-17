/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.ql.ast;

import java.util.List;
import lombok.Value;

@Value
public final class SelectOp implements PipelineOp {
  List<FieldRef> fields;

  public SelectOp(List<FieldRef> fields) {
    this.fields = fields;
  }
}

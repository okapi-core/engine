/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.eval.labelmatch;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.okapi.promql.parse.LabelMatcher;

@AllArgsConstructor
public class LabelCondition extends LabelMatchCtx {

  @Getter private LabelMatcher labelMatcher;

  @Override
  public TYPE getType() {
    return TYPE.LABEL_CONDITION;
  }
}

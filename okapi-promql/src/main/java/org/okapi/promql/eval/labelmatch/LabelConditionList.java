/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.eval.labelmatch;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.okapi.promql.parse.LabelMatcher;

@AllArgsConstructor
public class LabelConditionList extends LabelMatchCtx {
  @Getter List<LabelMatcher> conditions;

  @Override
  public TYPE getType() {
    return TYPE.LABEL_CONDITION_LIST;
  }
}

/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.eval.labelmatch;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.okapi.promql.parse.LabelMatcher;

@AllArgsConstructor
@Getter
public class MetricMatchCondition extends LabelMatchCtx {
  String metricNameOrNull;
  List<LabelMatcher> labelMatchers;

  @Override
  public TYPE getType() {
    return TYPE.METRIC_MATCH_CONDITION;
  }
}

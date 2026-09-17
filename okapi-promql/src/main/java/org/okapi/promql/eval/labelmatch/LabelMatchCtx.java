/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.eval.labelmatch;

public abstract class LabelMatchCtx {
  public abstract TYPE getType();

  public enum TYPE {
    LABEL_CONDITION,
    LABEL_CONDITION_LIST,
    METRIC_MATCH_CONDITION
  }
}

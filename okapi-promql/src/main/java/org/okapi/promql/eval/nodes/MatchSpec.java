/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.eval.nodes;

import java.util.List;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public final class MatchSpec {
  public final Mode mode;
  public final List<String> labels;
  public final boolean groupLeft;
  public final boolean groupRight;
  public final List<String> include;

  public enum Mode {
    ON,
    IGNORING
  }
}

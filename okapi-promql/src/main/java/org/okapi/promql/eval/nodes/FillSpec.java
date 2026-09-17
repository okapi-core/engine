/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.eval.nodes;

public record FillSpec(Double left, Double right) {
  public static FillSpec none() {
    return new FillSpec(null, null);
  }

  public boolean fillsLeft() {
    return left != null;
  }

  public boolean fillsRight() {
    return right != null;
  }
}

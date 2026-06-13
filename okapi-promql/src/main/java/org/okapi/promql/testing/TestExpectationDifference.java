/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.testing;

public record TestExpectationDifference(String expression, String message, String expected, String actual) {
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("expr=").append(expression).append(" ").append(message);
    if (expected != null) {
      sb.append(" expected=").append(expected);
    }
    if (actual != null) {
      sb.append(" actual=").append(actual);
    }
    return sb.toString();
  }

  public static TestExpectationDifference of(
      String expression, String message, String expected, String actual) {
    return new TestExpectationDifference(expression, message, expected, actual);
  }
}

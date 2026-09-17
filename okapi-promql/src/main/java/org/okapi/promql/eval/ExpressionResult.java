/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.eval;

public sealed interface ExpressionResult
    permits ScalarResult,
        StringResult,
        InstantVectorResult,
        RangeVectorResult,
        HistogramVectorResult {
  ValueType type();
}

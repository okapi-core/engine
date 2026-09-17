/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.eval;

import org.okapi.promql.eval.exceptions.EvaluationException;

public final class TypeChecks {
  private TypeChecks() {}

  public static InstantVectorResult requireInstantVector(ExpressionResult r, String fn)
      throws EvaluationException {
    if (r instanceof InstantVectorResult iv) return iv;
    throw new EvaluationException(fn + ": expected instant-vector, got " + r.type());
  }

  public static RangeVectorResult requireRangeVector(ExpressionResult r, String fn)
      throws EvaluationException {
    if (r instanceof RangeVectorResult rv) return rv;
    throw new EvaluationException(fn + ": expected range-vector, got " + r.type());
  }

  public static ScalarResult requireScalar(ExpressionResult r, String fn)
      throws EvaluationException {
    if (r instanceof ScalarResult s) return s;
    throw new EvaluationException(fn + ": expected scalar, got " + r.type());
  }
}

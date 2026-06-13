/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.eval.ops;

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.math3.util.FastMath;
import org.okapi.promql.eval.InstantVectorResult;
import org.okapi.promql.eval.VectorData.Labels;
import org.okapi.promql.eval.VectorData.Sample;
import org.okapi.promql.eval.VectorData.SeriesId;
import org.okapi.promql.eval.VectorData.SeriesSample;
import org.okapi.promql.eval.exceptions.EvaluationException;

/** Trigonometric functions delegated to Apache Commons Math. */
public final class TrigFunctions {
  private TrigFunctions() {}

  public static InstantVectorResult apply(String name, InstantVectorResult iv) {
    List<SeriesSample> out = new ArrayList<>(iv.data().size());
    for (var sample : iv.data()) {
      out.add(
          new SeriesSample(
              SeriesIds.derived(sample.series()),
              new Sample(
                  sample.sample().ts(),
                  sample.sample().sourceTs(),
                  apply(name, sample.sample().value()))));
    }
    return new InstantVectorResult(out);
  }

  private static double apply(String name, double value) {
    return switch (name) {
          case "sin" -> FastMath.sin(value);
          case "cos" -> FastMath.cos(value);
          case "tan" -> FastMath.tan(value);
          case "asin" -> FastMath.asin(value);
          case "acos" -> FastMath.acos(value);
          case "atan" -> FastMath.atan(value);
          case "sinh" -> FastMath.sinh(value);
          case "cosh" -> FastMath.cosh(value);
          case "tanh" -> FastMath.tanh(value);
          case "asinh" -> FastMath.asinh(value);
          case "acosh" -> FastMath.acosh(value);
          case "atanh" -> FastMath.atanh(value);
          case "rad" -> FastMath.toRadians(value);
          case "deg" -> FastMath.toDegrees(value);
          default -> throw new EvaluationException("unknown trig function: " + name);
        };
  }

}

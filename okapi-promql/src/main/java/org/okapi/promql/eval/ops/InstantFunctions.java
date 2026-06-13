/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.eval.ops;

import java.util.*;
import java.util.function.Function;
import org.okapi.promql.eval.*;
import org.okapi.promql.eval.VectorData.*;

/** Pure functions over InstantVectorResult. */
public final class InstantFunctions {
  private InstantFunctions() {}

  public static InstantVectorResult mapSamples(InstantVectorResult iv, Function<Float, Float> fn) {
    List<SeriesSample> out = new ArrayList<>(iv.data().size());
    for (var s : iv.data())
      out.add(new SeriesSample(s.series(), new Sample(s.sample().ts(), fn.apply(s.sample().value()))));
    return new InstantVectorResult(out);
  }

  public static InstantVectorResult clampMin(InstantVectorResult iv, float min) {
    return mapSamples(iv, v -> Math.max(v, min));
  }

  public static InstantVectorResult clampMax(InstantVectorResult iv, float max) {
    return mapSamples(iv, v -> Math.min(v, max));
  }

  public static InstantVectorResult sort(InstantVectorResult iv, boolean desc) {
    var data = new ArrayList<>(iv.data());
    data.sort(Comparator.comparingDouble(s -> s.sample().value()));
    if (desc) Collections.reverse(data);
    return new InstantVectorResult(data);
  }

  public static InstantVectorResult absent(InstantVectorResult iv, EvalContext ctx) {
    if (iv.data().isEmpty())
      return new InstantVectorResult(
          List.of(
              new SeriesSample(
                  new SeriesId("absent", new Labels(Map.of())), new Sample(ctx.endMs, 1f))));
    return new InstantVectorResult(List.of());
  }

  public static InstantVectorResult timestamp(InstantVectorResult iv) {
    List<SeriesSample> out = new ArrayList<>(iv.data().size());
    for (var s : iv.data()) {
      float secs = s.sample().ts() / 1000f;
      out.add(new SeriesSample(s.series(), new Sample(s.sample().ts(), secs)));
    }
    return new InstantVectorResult(out);
  }

  public static ScalarResult toScalar(InstantVectorResult iv) {
    if (iv.data().size() != 1) return new ScalarResult(Float.NaN);
    return new ScalarResult(iv.data().get(0).sample().value());
  }
}

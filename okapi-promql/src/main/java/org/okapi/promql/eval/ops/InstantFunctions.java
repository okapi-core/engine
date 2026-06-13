/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.eval.ops;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Function;
import org.okapi.promql.eval.*;
import org.okapi.promql.eval.VectorData.*;

/** Pure functions over InstantVectorResult. */
public final class InstantFunctions {
  private InstantFunctions() {}

  public static InstantVectorResult mapSamples(InstantVectorResult iv, Function<Float, Float> fn) {
    return mapSamples(iv, fn, false);
  }

  public static InstantVectorResult mapDerivedSamples(
      InstantVectorResult iv, Function<Float, Float> fn) {
    return mapSamples(iv, fn, true);
  }

  private static InstantVectorResult mapSamples(
      InstantVectorResult iv, Function<Float, Float> fn, boolean derived) {
    List<SeriesSample> out = new ArrayList<>(iv.data().size());
    for (var s : iv.data())
      out.add(
          new SeriesSample(
              derived ? SeriesIds.derived(s.series()) : s.series(),
              new Sample(s.sample().ts(), s.sample().sourceTs(), fn.apply(s.sample().value()))));
    return new InstantVectorResult(out);
  }

  public static InstantVectorResult clamp(InstantVectorResult iv, float min, float max) {
    return mapDerivedSamples(iv, v -> Math.max(min, Math.min(max, v)));
  }

  public static InstantVectorResult clampMin(InstantVectorResult iv, float min) {
    return mapDerivedSamples(iv, v -> Math.max(v, min));
  }

  public static InstantVectorResult clampMax(InstantVectorResult iv, float max) {
    return mapDerivedSamples(iv, v -> Math.min(v, max));
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
    for (var s : iv.data())
      out.add(
          new SeriesSample(
              SeriesIds.derived(s.series()),
              new Sample(s.sample().ts(), s.sample().sourceTs() / 1000f)));
    return new InstantVectorResult(out);
  }

  public static InstantVectorResult calendar(String name, InstantVectorResult iv, EvalContext ctx) {
    List<SeriesSample> out = new ArrayList<>();
    if (iv == null) {
      for (long t = ctx.startMs; t <= ctx.endMs; t += ctx.stepMs) {
        out.add(
            new SeriesSample(
                new SeriesId("", new Labels(Map.of())),
                new Sample(t, calendarValue(name, t / 1000f))));
      }
      return new InstantVectorResult(out);
    }

    for (var s : iv.data()) {
      out.add(
          new SeriesSample(
              SeriesIds.derived(s.series()),
              new Sample(s.sample().ts(), calendarValue(name, s.sample().value()))));
    }
    return new InstantVectorResult(out);
  }

  private static float calendarValue(String name, float unixSeconds) {
    ZonedDateTime time =
        ZonedDateTime.ofInstant(Instant.ofEpochSecond((long) unixSeconds), ZoneOffset.UTC);
    return switch (name) {
      case "year" -> time.getYear();
      case "month" -> time.getMonthValue();
      case "day_of_month" -> time.getDayOfMonth();
      case "day_of_week" -> time.getDayOfWeek().getValue() % 7;
      case "day_of_year" -> time.getDayOfYear();
      case "days_in_month" -> time.toLocalDate().lengthOfMonth();
      case "hour" -> time.getHour();
      case "minute" -> time.getMinute();
      default -> throw new IllegalArgumentException("unknown calendar function: " + name);
    };
  }

  public static ScalarResult toScalar(InstantVectorResult iv) {
    if (iv.data().size() != 1) return new ScalarResult(Float.NaN);
    return new ScalarResult(iv.data().get(0).sample().value());
  }
}

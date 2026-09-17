/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.traces.red;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.okapi.metrics.pojos.RES_TYPE;

@AllArgsConstructor
@Getter
@Builder
@NoArgsConstructor
@JsonClassDescription(
    "Time-series RED metrics: request rate, error rate, and duration percentiles, all aligned to the same time buckets.")
public class RedMetrics {
  @JsonPropertyDescription("Bucket start timestamps in milliseconds since Unix epoch.")
  @NotNull
  List<Long> ts;

  @JsonPropertyDescription("Request count per bucket, index-aligned with ts.")
  @NotNull
  List<Long> counts;

  @JsonPropertyDescription("Requests per second per bucket, index-aligned with ts.")
  @NotNull
  List<Double> rps;

  @JsonPropertyDescription("Requests per minute per bucket, index-aligned with ts.")
  @NotNull
  List<Double> rpm;

  @JsonPropertyDescription("Error ratio per bucket, index-aligned with ts.")
  @NotNull
  List<Double> errorRates;

  @JsonPropertyDescription(
      "P50 (median) request duration in milliseconds per bucket, index-aligned with ts.")
  @NotNull
  List<Double> durationsP50;

  @JsonPropertyDescription(
      "P75 request duration in milliseconds per bucket, index-aligned with ts.")
  @NotNull
  List<Double> durationsP75;

  @JsonPropertyDescription(
      "P90 request duration in milliseconds per bucket, index-aligned with ts.")
  @NotNull
  List<Double> durationsP90;

  @JsonPropertyDescription(
      "P99 request duration in milliseconds per bucket, index-aligned with ts.")
  @NotNull
  List<Double> durationsP99;

  @JsonPropertyDescription("Error count per bucket, index-aligned with ts.")
  @NotNull
  List<Long> errors;

  @JsonPropertyDescription("Total request count across all returned buckets.")
  @NotNull
  Long totalRequests;

  @JsonPropertyDescription("Total error count across all returned buckets.")
  @NotNull
  Long totalErrors;

  @JsonPropertyDescription(
      "Availability across all returned buckets: 1 - totalErrors / totalRequests. Null when no requests were observed.")
  Double availability;

  public static RedMetrics of(
      List<Long> ts, List<Long> counts, List<Long> errors, List<Double> values) {
    return of(ts, counts, errors, values, RES_TYPE.SECONDLY);
  }

  public static RedMetrics of(
      List<Long> ts, List<Long> counts, List<Long> errors, List<Double> values, RES_TYPE resType) {
    var bucketSeconds = bucketSeconds(resType);
    var rps = counts.stream().map(count -> count / bucketSeconds).toList();
    var rpm = counts.stream().map(count -> count * 60.0 / bucketSeconds).toList();
    var errorRates =
        java.util.stream.IntStream.range(0, counts.size())
            .mapToObj(i -> counts.get(i) == 0 ? 0.0 : errors.get(i) / (double) counts.get(i))
            .toList();
    long totalRequests = counts.stream().mapToLong(Long::longValue).sum();
    long totalErrors = errors.stream().mapToLong(Long::longValue).sum();
    return RedMetrics.builder()
        .ts(ts)
        .counts(counts)
        .rps(rps)
        .rpm(rpm)
        .errorRates(errorRates)
        .errors(errors)
        .durationsP50(values)
        .durationsP75(values)
        .durationsP90(values)
        .durationsP99(values)
        .totalRequests(totalRequests)
        .totalErrors(totalErrors)
        .availability(totalRequests == 0 ? null : 1.0 - totalErrors / (double) totalRequests)
        .build();
  }

  private static double bucketSeconds(RES_TYPE resType) {
    var effective = resType == null ? RES_TYPE.SECONDLY : resType;
    return switch (effective) {
      case SECONDLY -> 1.0;
      case MINUTELY -> 60.0;
      case HOURLY -> 3600.0;
    };
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (other == null || getClass() != other.getClass()) return false;
    RedMetrics that = (RedMetrics) other;
    return Objects.equals(ts, that.ts)
        && Objects.equals(counts, that.counts)
        && Objects.equals(rps, that.rps)
        && Objects.equals(rpm, that.rpm)
        && Objects.equals(errorRates, that.errorRates)
        && Objects.equals(durationsP50, that.durationsP50)
        && Objects.equals(durationsP75, that.durationsP75)
        && Objects.equals(durationsP90, that.durationsP90)
        && Objects.equals(durationsP99, that.durationsP99)
        && Objects.equals(errors, that.errors)
        && Objects.equals(totalRequests, that.totalRequests)
        && Objects.equals(totalErrors, that.totalErrors)
        && Objects.equals(availability, that.availability);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        ts,
        counts,
        rps,
        rpm,
        errorRates,
        durationsP50,
        durationsP75,
        durationsP90,
        durationsP99,
        errors,
        totalRequests,
        totalErrors,
        availability);
  }
}

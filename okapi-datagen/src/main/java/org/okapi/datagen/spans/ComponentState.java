/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.datagen.spans;

import java.util.Map;
import java.util.Random;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

@Value
@Builder
public class ComponentState {
  double successRate;
  @Singular Map<ErrorType, Double> errorRates;
  @Singular Map<ErrorType, String> errorMessages;

  @Builder.Default
  LatencyConfig latency = LatencyConfig.builder().minMs(5).maxMs(20).timeoutPenaltyMs(40).build();

  public static ComponentState defaultState() {
    return ComponentState.builder()
        .successRate(1.0)
        .latency(LatencyConfig.builder().minMs(5).maxMs(20).timeoutPenaltyMs(40).build())
        .build();
  }

  public Outcome sampleOutcome(Random random) {
    double roll = random.nextDouble();
    if (roll <= successRate) {
      return Outcome.SUCCESS;
    }
    double remaining = roll - successRate;
    double acc = 0.0;
    for (var entry : errorRates.entrySet()) {
      acc += entry.getValue();
      if (remaining <= acc) {
        return Outcome.fromErrorType(entry.getKey());
      }
    }
    return Outcome.APP_ERROR;
  }

  public long sampleDurationNs(Random random, Outcome outcome) {
    long baseMs = latency.sampleMs(random);
    if (outcome == Outcome.TIMEOUT) {
      baseMs += latency.getTimeoutPenaltyMs();
    }
    return baseMs * 1_000_000L;
  }

  public String errorMessage(Outcome outcome) {
    return switch (outcome) {
      case TIMEOUT -> errorMessages.getOrDefault(ErrorType.TIMEOUT, "timeout");
      case APP_ERROR -> errorMessages.getOrDefault(ErrorType.APP_ERROR, "application error");
      case DEPENDENCY_ERROR ->
          errorMessages.getOrDefault(ErrorType.DEPENDENCY_ERROR, "dependency error");
      case CRASH -> errorMessages.getOrDefault(ErrorType.CRASH, "process crashed");
      default -> "";
    };
  }
}

/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.datagen.spans;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SpansGeneratorConfig {
  long seed;
  int traceCount;
  long baseStartMs;
  long traceSpacingMs;
  long stepGapMs;
  long childOffsetMs;
  long childGapMs;
  long tailMs;
  @Builder.Default ComponentState defaultComponentState = ComponentState.defaultState();
  @Builder.Default List<SystemState> states = List.of(SystemState.defaultState());
  @Builder.Default Journey journey = Journey.defaultJourney();

  public static SpansGeneratorConfig defaultConfig() {
    var defaultLatency = LatencyConfig.builder().minMs(10).maxMs(80).timeoutPenaltyMs(120).build();
    var slowLatency = LatencyConfig.builder().minMs(50).maxMs(250).timeoutPenaltyMs(400).build();

    var defaultComponent =
        ComponentState.builder()
            .successRate(0.98)
            .latency(defaultLatency)
            .errorRates(
                Map.of(
                    ErrorType.TIMEOUT, 0.01,
                    ErrorType.APP_ERROR, 0.01))
            .errorMessages(
                Map.of(
                    ErrorType.TIMEOUT, "request timeout",
                    ErrorType.APP_ERROR, "unexpected error"))
            .build();

    var paymentDegraded =
        SystemState.builder()
            .name("payment-degraded")
            .weight(0.2)
            .components(
                Map.of(
                    "payment-service",
                        ComponentState.builder()
                            .successRate(0.8)
                            .latency(slowLatency)
                            .errorRates(
                                Map.of(
                                    ErrorType.TIMEOUT, 0.1,
                                    ErrorType.APP_ERROR, 0.05,
                                    ErrorType.DEPENDENCY_ERROR, 0.05))
                            .errorMessages(
                                Map.of(
                                    ErrorType.TIMEOUT, "payment timeout",
                                    ErrorType.APP_ERROR, "card declined",
                                    ErrorType.DEPENDENCY_ERROR, "gateway unavailable"))
                            .build(),
                    "payment-gateway",
                        ComponentState.builder()
                            .successRate(0.7)
                            .latency(slowLatency)
                            .errorRates(
                                Map.of(
                                    ErrorType.TIMEOUT, 0.2,
                                    ErrorType.DEPENDENCY_ERROR, 0.1))
                            .errorMessages(
                                Map.of(
                                    ErrorType.TIMEOUT, "gateway timeout",
                                    ErrorType.DEPENDENCY_ERROR, "gateway error"))
                            .build()))
            .build();

    var inventoryOutage =
        SystemState.builder()
            .name("inventory-outage")
            .weight(0.2)
            .components(
                Map.of(
                    "inventory-service",
                    ComponentState.builder()
                        .successRate(0.7)
                        .latency(defaultLatency)
                        .errorRates(
                            Map.of(
                                ErrorType.APP_ERROR, 0.2,
                                ErrorType.DEPENDENCY_ERROR, 0.1))
                        .errorMessages(
                            Map.of(
                                ErrorType.APP_ERROR, "out of stock",
                                ErrorType.DEPENDENCY_ERROR, "db unavailable"))
                        .build()))
            .build();

    var shippingSlow =
        SystemState.builder()
            .name("shipping-slow")
            .weight(0.2)
            .components(
                Map.of(
                    "shipping-service",
                    ComponentState.builder()
                        .successRate(0.95)
                        .latency(slowLatency)
                        .errorRates(Map.of(ErrorType.TIMEOUT, 0.05))
                        .errorMessages(Map.of(ErrorType.TIMEOUT, "rate limit timeout"))
                        .build()))
            .build();

    var healthy =
        SystemState.builder()
            .name("healthy")
            .weight(0.4)
            .components(Collections.emptyMap())
            .build();

    return SpansGeneratorConfig.builder()
        .seed(42L)
        .traceCount(20)
        .baseStartMs(System.currentTimeMillis() - 60_000L)
        .traceSpacingMs(2000L)
        .stepGapMs(50L)
        .childOffsetMs(10L)
        .childGapMs(15L)
        .tailMs(5L)
        .defaultComponentState(defaultComponent)
        .states(List.of(healthy, paymentDegraded, inventoryOutage, shippingSlow))
        .journey(Journey.defaultJourney())
        .build();
  }
}

/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.spring.configs;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.core.instrument.binder.system.UptimeMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.registry.otlp.OtlpConfig;
import io.micrometer.registry.otlp.OtlpMeterRegistry;
import java.time.Duration;
import org.okapi.telemetry.OkapiInternalMetrics;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MeteringConfiguration {

  @Bean
  @ConditionalOnMissingBean(MeterRegistry.class)
  public MeterRegistry meterRegistry(
      @Value("${okapi.telemetry.otlp.metrics.endpoint:}") String endpoint,
      @Value("${okapi.telemetry.otlp.metrics.step:15s}") Duration step) {
    MeterRegistry registry;
    if (endpoint == null || endpoint.isBlank()) {
      registry = new SimpleMeterRegistry();
    } else {
      OtlpConfig config =
          new OtlpConfig() {
            @Override
            public String get(String key) {
              return switch (key) {
                case "url" -> endpoint;
                case "step" -> step.toString();
                default -> null;
              };
            }
          };
      registry = new OtlpMeterRegistry(config, Clock.SYSTEM);
    }
    bindRuntimeMetrics(registry);
    return registry;
  }

  @Bean
  public OkapiInternalMetrics okapiInternalMetrics(MeterRegistry meterRegistry) {
    return new OkapiInternalMetrics(meterRegistry);
  }

  private static void bindRuntimeMetrics(MeterRegistry registry) {
    new ClassLoaderMetrics().bindTo(registry);
    new JvmGcMetrics().bindTo(registry);
    new JvmMemoryMetrics().bindTo(registry);
    new JvmThreadMetrics().bindTo(registry);
    new ProcessorMetrics().bindTo(registry);
    new UptimeMetrics().bindTo(registry);
  }
}

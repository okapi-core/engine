/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.demo.config;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.semconv.ServiceAttributes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TelemetryConfig {

  @Bean(destroyMethod = "shutdown")
  public SdkTracerProvider sdkTracerProvider(
      @Value("${otel.exporter.otlp.endpoint:http://localhost:4317}") String otlpEndpoint,
      @Value("${otel.service.name:okapi-demo-server}") String serviceName) {
    var resource =
        Resource.getDefault()
            .merge(Resource.create(Attributes.of(ServiceAttributes.SERVICE_NAME, serviceName)));

    var exporter = OtlpGrpcSpanExporter.builder().setEndpoint(otlpEndpoint).build();
    return SdkTracerProvider.builder()
        .setResource(resource)
        .addSpanProcessor(BatchSpanProcessor.builder(exporter).build())
        .build();
  }

  @Bean
  public OpenTelemetry openTelemetry(SdkTracerProvider sdkTracerProvider) {
    var openTelemetry =
        OpenTelemetrySdk.builder()
            .setTracerProvider(sdkTracerProvider)
            .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
            .build();
    GlobalOpenTelemetry.set(openTelemetry);
    return openTelemetry;
  }

  @Bean
  public Tracer tracer(OpenTelemetry openTelemetry) {
    return openTelemetry.getTracer("org.okapi.demo");
  }

  @Bean
  public TextMapPropagator textMapPropagator(OpenTelemetry openTelemetry) {
    return openTelemetry.getPropagators().getTextMapPropagator();
  }
}

/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.spring.configs;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

@Component
@RequiredArgsConstructor
public class OkapiHttpMetricsInterceptor implements HandlerInterceptor {
  private static final String START_NANOS = OkapiHttpMetricsInterceptor.class.getName() + ".start";

  private final MeterRegistry meterRegistry;
  private final AtomicInteger inFlight = new AtomicInteger();

  @PostConstruct
  void registerGauges() {
    meterRegistry.gauge("okapi.http.server.in_flight", inFlight);
  }

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    request.setAttribute(START_NANOS, System.nanoTime());
    inFlight.incrementAndGet();
    return true;
  }

  @Override
  public void afterCompletion(
      HttpServletRequest request,
      HttpServletResponse response,
      Object handler,
      Exception exception) {
    var startNanos = (Long) request.getAttribute(START_NANOS);
    if (startNanos == null) {
      return;
    }
    inFlight.decrementAndGet();
    var route = route(request);
    var method = request.getMethod();
    var status = Integer.toString(response.getStatus());
    var outcome =
        response.getStatus() >= 500
            ? "server_error"
            : response.getStatus() >= 400 ? "client_error" : "success";
    var tags =
        new String[] {
          "component",
          "ingester",
          "route",
          route,
          "method",
          method,
          "status",
          status,
          "outcome",
          outcome
        };
    Counter.builder("okapi.http.server.requests").tags(tags).register(meterRegistry).increment();
    Timer.builder("okapi.http.server.duration")
        .tags(tags)
        .publishPercentileHistogram()
        .register(meterRegistry)
        .record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
  }

  private static String route(HttpServletRequest request) {
    var pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
    return pattern == null ? "unknown" : pattern.toString();
  }
}

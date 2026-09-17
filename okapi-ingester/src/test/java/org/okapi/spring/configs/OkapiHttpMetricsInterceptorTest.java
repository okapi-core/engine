/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.spring.configs;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

class OkapiHttpMetricsInterceptorTest {

  @Test
  void recordsTemplatedRouteMetricsAndBalancesInFlightGauge() throws Exception {
    var registry = new SimpleMeterRegistry();
    var interceptor = new OkapiHttpMetricsInterceptor(registry);
    interceptor.registerGauges();

    var request = new MockHttpServletRequest("POST", "/api/v1/logs/query");
    request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/v1/logs/query");
    var response = new MockHttpServletResponse();

    interceptor.preHandle(request, response, new Object());
    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    interceptor.afterCompletion(request, response, new Object(), new RuntimeException("failure"));

    assertEquals(
        1.0,
        registry
            .get("okapi.http.server.requests")
            .tag("route", "/api/v1/logs/query")
            .tag("outcome", "server_error")
            .counter()
            .count());
    assertEquals(0.0, registry.get("okapi.http.server.in_flight").gauge().value());
    assertEquals(
        1.0, registry.get("okapi.http.server.duration").tag("status", "500").timer().count());
  }
}

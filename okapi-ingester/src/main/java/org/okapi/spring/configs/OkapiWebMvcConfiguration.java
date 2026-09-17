/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.spring.configs;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class OkapiWebMvcConfiguration implements WebMvcConfigurer {
  private final OkapiHttpMetricsInterceptor metricsInterceptor;

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(metricsInterceptor);
  }
}

/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.spring.config;

import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class StaticResourceConfiguration implements WebMvcConfigurer {

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry
        .addResourceHandler("/assets/**")
        .addResourceLocations("classpath:/public/assets/")
        .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable());

    registry
        .addResourceHandler("/index.html")
        .addResourceLocations("classpath:/public/")
        .setCacheControl(CacheControl.noCache());
  }
}

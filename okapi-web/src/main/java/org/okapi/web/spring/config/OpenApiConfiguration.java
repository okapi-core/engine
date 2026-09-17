/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.spring.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

  @Bean
  OpenAPI okapiOpenApi() {
    return new OpenAPI().info(new Info().title("Okapi Web API").version("0.0.1-SNAPSHOT"));
  }
}

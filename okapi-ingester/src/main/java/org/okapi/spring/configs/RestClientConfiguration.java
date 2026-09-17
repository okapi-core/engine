/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.spring.configs;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfiguration {

  @Bean
  public RestClient restClient() {
    return RestClient.create();
  }
}

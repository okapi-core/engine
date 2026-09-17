/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.staticserver;

import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StaticServerConfiguration {

  @Bean
  public OkHttpClient httpClient() {
    return new OkHttpClient.Builder()
        .followRedirects(true) // Follow redirects
        .followSslRedirects(true) // Follow SSL redirects
        .build();
  }
}

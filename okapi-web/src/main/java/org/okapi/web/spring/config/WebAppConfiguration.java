/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.spring.config;

import com.google.gson.Gson;
import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WebAppConfiguration {

  @Bean
  public OkHttpClient httpClient() {
    return new OkHttpClient();
  }

  @Bean
  public Gson gson() {
    return new Gson();
  }
}

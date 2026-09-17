/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.spring.config;

import okhttp3.OkHttpClient;
import org.okapi.ingester.client.IngesterClient;
import org.okapi.ingester.client.PromQlQueryClient;
import org.okapi.ingester.client.ProxyResponseTranslator;
import org.okapi.oscar.client.OscarClient;
import org.okapi.web.service.Configs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProxyConfig {

  @Bean
  public ProxyResponseTranslator proxyResponseTranslator() {
    return new ProxyResponseTranslator();
  }

  @Bean
  public IngesterClient ingesterClient(
      @Value(Configs.CLUSTER_EP) String clusterEp,
      OkHttpClient okHttpClient,
      ProxyResponseTranslator responseTranslator) {
    return new IngesterClient(clusterEp, okHttpClient, responseTranslator);
  }

  @Bean
  public PromQlQueryClient promQlQueryClient(
      @Value(Configs.CLUSTER_EP) String clusterEp,
      OkHttpClient okHttpClient,
      ProxyResponseTranslator responseTranslator) {
    return new PromQlQueryClient(clusterEp, okHttpClient, responseTranslator);
  }

  @Bean
  public OscarClient oscarClient(
      @Value(Configs.OSCAR_EP) String oscarEp,
      OkHttpClient okHttpClient,
      ProxyResponseTranslator responseTranslator) {
    return new OscarClient(oscarEp, okHttpClient, responseTranslator);
  }
}

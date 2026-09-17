/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.oscar.spring;

import okhttp3.OkHttpClient;
import org.okapi.ingester.client.IngesterClient;
import org.okapi.ingester.client.ProxyResponseTranslator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class IngesterClientConfig {
  @Bean
  public ProxyResponseTranslator responseTranslator() {
    return new ProxyResponseTranslator();
  }

  @Bean
  public IngesterClient ingesterClient(
      @Value(ConfigKeys.CLUSTER_EP) String clusterEndpoint,
      OkHttpClient httpClient,
      ProxyResponseTranslator responseTranslator) {
    return new IngesterClient(clusterEndpoint, httpClient, responseTranslator);
  }
}

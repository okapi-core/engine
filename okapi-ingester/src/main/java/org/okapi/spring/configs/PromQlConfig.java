/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.spring.configs;

import com.clickhouse.client.api.Client;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.okapi.beans.Configurations;
import org.okapi.metrics.ch.template.ChMetricTemplateEngine;
import org.okapi.promql.ch.ChPromQlSeriesDiscoveryFactory;
import org.okapi.promql.ch.ChPromQlTsClientFactory;
import org.okapi.promql.eval.ts.StatisticsMerger;
import org.okapi.promql.query.NoOpStatisticsMerger;
import org.okapi.promql.query.PromQlMetadataService;
import org.okapi.promql.query.PromQlQueryProcessor;
import org.okapi.promql.runtime.SeriesDiscoveryFactory;
import org.okapi.promql.runtime.TsClientFactory;
import org.okapi.rest.promql.Sample;
import org.okapi.rest.promql.SampleAdapter;
import org.okapi.spring.configs.properties.PromQlQueryCfg;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PromQlConfig {

  @Bean(name = Configurations.BEAN_PROMQL_SERIALIZER)
  public Gson promqlSerializer() {
    return new GsonBuilder().registerTypeAdapter(Sample.class, new SampleAdapter()).create();
  }

  @Bean(name = "promqlExecutor")
  public ExecutorService promqlExecutor(@Autowired PromQlQueryCfg promQlQueryCfg) {
    return Executors.newFixedThreadPool(promQlQueryCfg.getEvalThreads());
  }

  @Bean
  public StatisticsMerger statisticsMerger() {
    return new NoOpStatisticsMerger();
  }

  @Bean
  public TsClientFactory tsClientFactory(
      @Autowired Client client, @Autowired ChMetricTemplateEngine templateEngine) {
    return new ChPromQlTsClientFactory(client, templateEngine);
  }

  @Bean
  public SeriesDiscoveryFactory seriesDiscoveryFactory(
      @Autowired Client client, @Autowired ChMetricTemplateEngine templateEngine) {
    return new ChPromQlSeriesDiscoveryFactory(client, templateEngine);
  }

  @Bean
  public PromQlQueryProcessor promQlQueryProcessor(
      @Autowired @Qualifier("promqlExecutor") ExecutorService promqlExecutor,
      @Autowired StatisticsMerger statisticsMerger,
      @Autowired TsClientFactory tsClientFactory,
      @Autowired SeriesDiscoveryFactory seriesDiscoveryFactory) {
    return new PromQlQueryProcessor(
        promqlExecutor, statisticsMerger, tsClientFactory, seriesDiscoveryFactory);
  }

  @Bean
  public PromQlMetadataService promQlMetadataService(
      @Autowired Client client, @Autowired ChMetricTemplateEngine templateEngine) {
    return new PromQlMetadataService(client, templateEngine);
  }
}

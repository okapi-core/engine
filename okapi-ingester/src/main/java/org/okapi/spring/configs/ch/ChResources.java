/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.spring.configs.ch;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.enums.Protocol;
import java.io.IOException;
import org.okapi.metrics.ch.ChWalResources;
import org.okapi.spring.configs.Qualifiers;
import org.okapi.wal.manager.WalManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChResources {
  @Bean
  public Client getClient(@Autowired ChConfig chConfig) {
    return new Client.Builder()
        .addEndpoint(Protocol.HTTP, chConfig.getHost(), chConfig.getPort(), chConfig.isSecure())
        .setUsername(chConfig.getUserName())
        .setPassword(chConfig.getPassword())
        .build();
  }

  @Bean(name = Qualifiers.METRICS_CH_WAL_RESOURCES)
  @ConditionalOnProperty(
      name = "okapi.metrics.consumptionType",
      havingValue = "wal",
      matchIfMissing = true)
  public ChWalResources metricsChWalResources(@Autowired ChConfig chConfig) throws IOException {
    return new ChWalResources(
        chConfig.getChMetricsWal(),
        new WalManager.WalConfig(chConfig.getChMetricsWalCfg().getSegmentSize()));
  }

  @Bean(name = Qualifiers.LOGS_CH_WAL_RESOURCES)
  public ChWalResources logsChWalResources(@Autowired ChConfig chConfig) throws IOException {
    return new ChWalResources(
        chConfig.getChLogsWal(),
        new WalManager.WalConfig(chConfig.getChLogsCfg().getSegmentSize()));
  }

  @Bean(name = Qualifiers.TRACES_CH_WAL_RESOURCES)
  public ChWalResources tracesChWalResources(@Autowired ChConfig chConfig) throws IOException {
    return new ChWalResources(
        chConfig.getChTracesWal(),
        new WalManager.WalConfig(chConfig.getChTracesWalCfg().getSegmentSize()));
  }
}

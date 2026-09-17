/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.spring.configs.ch;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.enums.Protocol;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.okapi.metrics.ch.ChWalResources;
import org.okapi.spring.configs.Profiles;
import org.okapi.spring.configs.Qualifiers;
import org.okapi.telemetry.OkapiInternalMetrics;
import org.okapi.wal.manager.WalManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile(Profiles.PROFILE_CH)
@Slf4j
public class ChResources {
  @Bean
  public Client getClient(@Autowired ChConfig chConfig) {
    log.info(
        "Creating ClickHouse client: protocol={}, host={}, port={}, secure={}, username={}",
        Protocol.HTTP,
        chConfig.getHost(),
        chConfig.getPort(),
        chConfig.isSecure(),
        chConfig.getUserName());
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
  public ChWalResources metricsChWalResources(
      @Autowired ChConfig chConfig, @Autowired OkapiInternalMetrics metrics) throws IOException {
    log.info(
        "Creating metrics ClickHouse WAL resources: path={}, segmentSize={}",
        chConfig.getChMetricsWal(),
        chConfig.getChMetricsWalCfg().getSegmentSize());
    return new ChWalResources(
        chConfig.getChMetricsWal(),
        new WalManager.WalConfig(chConfig.getChMetricsWalCfg().getSegmentSize()),
        "metrics",
        metrics);
  }

  @Bean(name = Qualifiers.LOGS_CH_WAL_RESOURCES)
  @ConditionalOnProperty(
      name = "okapi.logs.consumptionType",
      havingValue = "wal",
      matchIfMissing = true)
  public ChWalResources logsChWalResources(
      @Autowired ChConfig chConfig, @Autowired OkapiInternalMetrics metrics) throws IOException {
    log.info(
        "Creating logs ClickHouse WAL resources: path={}, segmentSize={}",
        chConfig.getChLogsWal(),
        chConfig.getChLogsCfg().getSegmentSize());
    return new ChWalResources(
        chConfig.getChLogsWal(),
        new WalManager.WalConfig(chConfig.getChLogsCfg().getSegmentSize()),
        "logs",
        metrics);
  }

  @Bean(name = Qualifiers.TRACES_CH_WAL_RESOURCES)
  @ConditionalOnProperty(
      name = "okapi.traces.consumptionType",
      havingValue = "wal",
      matchIfMissing = true)
  public ChWalResources tracesChWalResources(
      @Autowired ChConfig chConfig, @Autowired OkapiInternalMetrics metrics) throws IOException {
    log.info(
        "Creating traces ClickHouse WAL resources: path={}, segmentSize={}",
        chConfig.getChTracesWal(),
        chConfig.getChTracesWalCfg().getSegmentSize());
    return new ChWalResources(
        chConfig.getChTracesWal(),
        new WalManager.WalConfig(chConfig.getChTracesWalCfg().getSegmentSize()),
        "traces",
        metrics);
  }
}

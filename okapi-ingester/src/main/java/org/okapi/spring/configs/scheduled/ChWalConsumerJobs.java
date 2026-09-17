/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.spring.configs.scheduled;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.okapi.runtime.ch.ChWalConsumerCommonDriver;
import org.okapi.spring.configs.properties.ChWalConsumerCfg;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class ChWalConsumerJobs implements SchedulingConfigurer {
  private final ChWalConsumerCommonDriver commonDriver;
  private final ChWalConsumerCfg walConsumerCfg;

  @Override
  public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
    taskRegistrar.addFixedDelayTask(
        commonDriver::onTick, Duration.ofMillis(walConsumerCfg.getConsumeIntervalMs()));
  }
}

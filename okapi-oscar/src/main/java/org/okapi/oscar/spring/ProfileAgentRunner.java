/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.oscar.spring;

import org.okapi.oscar.profile.ProfileAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProfileAgentRunner {
  private static final Logger LOGGER = LoggerFactory.getLogger(ProfileAgentRunner.class);

  @Bean
  @ConditionalOnProperty(
      prefix = "okapi.oscar.profile",
      name = "run-on-startup",
      havingValue = "true")
  public ApplicationRunner runProfile(ProfileAgent profileAgent) {
    return args -> LOGGER.info("Profile agent response: {}", profileAgent.runProfile());
  }
}

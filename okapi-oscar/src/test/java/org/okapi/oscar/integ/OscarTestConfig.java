/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.oscar.integ;

import org.mockito.Mockito;
import org.okapi.oscar.agents.OscarResearchAgent;
import org.okapi.oscar.agents.TestOscarResearchAgent;
import org.okapi.oscar.spring.cfg.OkapiOscarCfg;
import org.okapi.oscar.tools.DateTimeTools;
import org.okapi.oscar.tools.FilterContributionTool;
import org.okapi.oscar.tools.GreetingTools;
import org.okapi.oscar.tools.StatefulToolFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class OscarTestConfig {

  @Bean
  @Primary
  public OscarResearchAgent oscarResearchAgent(StatefulToolFactory statefulToolFactory) {
    return new TestOscarResearchAgent(
        Mockito.mock(ChatClient.class),
        Mockito.mock(OkapiOscarCfg.class),
        Mockito.mock(DateTimeTools.class),
        Mockito.mock(GreetingTools.class),
        Mockito.mock(FilterContributionTool.class),
        statefulToolFactory);
  }
}

/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.oscar.profile;

import lombok.AllArgsConstructor;
import org.okapi.oscar.spring.cfg.OkapiOscarCfg;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!dummy")
@AllArgsConstructor
public class ProfileAgent {
  private final ChatClient chatClient;
  private final ProfileTool profileTool;
  private final OkapiOscarCfg properties;

  public String runProfile() {
    return chatClient
        .prompt()
        .system(properties.getSystemPrompt())
        .user("Get the system prompt for Oscar. Do not prefix responses, provide the prompt as is.")
        .tools(profileTool)
        .call()
        .content();
  }
}

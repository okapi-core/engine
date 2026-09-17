/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.oscar.profile;

import lombok.AllArgsConstructor;
import org.okapi.oscar.spring.cfg.OkapiOscarCfg;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class ProfileTool {
  private final OkapiOscarCfg properties;

  @Tool(name = "Profile", description = "Return system prompt and purpose.")
  public String profile() {
    return properties.getSystemPrompt();
  }
}

/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.oscar.tools;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.okapi.oscar.spring.cfg.OkapiOscarGreetingCfg;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@AllArgsConstructor
public class GreetingTools {
  private static final String DEFAULT_GREETING = "Hello!";

  private final OkapiOscarGreetingCfg cfg;

  @Tool(
      description =
          """
  Return a random greeting from a preconfigured list.
  Use this tool whenever the user submits a greeting.
  This tool only selects the greeting; it does not send the greeting to the user.
  After this tool returns, you MUST call postResponse with the exact returned greeting.
  Do not return DONE until postResponse has been called.
  Use the pre-approved greeting instead of generating a free-form response.
  This applies to greetings such as `hello`, `Hi!`, or other open-ended messages unrelated to root-cause analysis.
  """)
  public String randomGreeting() {
    List<String> greetings = cfg.getGreetings();
    if (greetings == null || greetings.isEmpty()) {
      log.warn("No greetings configured; using default greeting.");
      return DEFAULT_GREETING;
    }
    int idx = ThreadLocalRandom.current().nextInt(greetings.size());
    return greetings.get(idx);
  }
}

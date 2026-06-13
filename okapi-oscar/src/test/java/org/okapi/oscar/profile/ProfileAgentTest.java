package org.okapi.oscar.profile;


import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.okapi.oscar.spring.cfg.OkapiOscarCfg;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "okapi.oscar.model.system-prompt=\"An AI SRE agent\""
})
@Disabled("Requires a real OpenAI API key")
class ProfileAgentTest {
  @Autowired private ProfileAgent profileAgent;
  @Autowired
  OkapiOscarCfg oscarCfg;

  @Test
  void runsProfileTool() {
    String response = profileAgent.runProfile();
    Assertions.assertTrue(response.contains(oscarCfg.getSystemPrompt()));
  }
}

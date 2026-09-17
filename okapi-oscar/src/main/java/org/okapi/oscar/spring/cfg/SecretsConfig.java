/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.oscar.spring.cfg;

import java.util.Optional;
import org.okapi.oscar.secrets.SecretsPathReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.vault.core.VaultTemplate;

@Configuration
public class SecretsConfig {
  @Bean
  public SecretsPathReader secretsPathReader(Optional<VaultTemplate> vaultTemplate) {
    return new SecretsPathReader(vaultTemplate);
  }
}

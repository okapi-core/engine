/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.oscar.spring;

import java.net.URI;
import lombok.extern.slf4j.Slf4j;
import org.okapi.oscar.spring.cfg.VaultCfg;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.VaultTemplate;

@Slf4j
@Configuration
public class VaultConfig {
  @Bean
  @ConditionalOnProperty(prefix = "okapi.oscar.vault", name = "address")
  public VaultTemplate vaultTemplate(@Autowired VaultCfg vaultCfg) {
    String address = vaultCfg.getAddress();
    String token = vaultCfg.getToken();
    log.info("Starting vault creation with cfg: {}", vaultCfg);
    if (address == null || address.isBlank()) {
      return null;
    }
    if (token == null || token.isBlank()) {
      throw new IllegalStateException("Vault token must be configured when using Vault");
    }
    VaultEndpoint endpoint = VaultEndpoint.from(URI.create(address));
    return new VaultTemplate(endpoint, new TokenAuthentication(token));
  }
}

/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.oscar.secrets;

import java.util.Optional;
import lombok.AllArgsConstructor;
import org.springframework.vault.core.VaultTemplate;

@AllArgsConstructor
public class SecretsPathReader {
  private static final String ENV_PREFIX = "env://";
  private static final String VAULT_PREFIX = "vault://";

  private final Optional<VaultTemplate> vaultTemplate;

  public ApiKeyProvider resolve(String path) {
    if (path == null || path.isBlank()) {
      throw new IllegalArgumentException("API key path must be configured");
    }
    if (path.startsWith(ENV_PREFIX)) {
      String envVar = path.substring(ENV_PREFIX.length());
      return new EnvApiKeyProvider(envVar);
    }
    if (path.startsWith(VAULT_PREFIX)) {
      String withoutScheme = path.substring(VAULT_PREFIX.length());
      String[] parts = withoutScheme.split("#", 2);
      String vaultPath = parts[0];
      String key = parts.length > 1 ? parts[1] : null;
      return new VaultApiKeyProvider(vaultTemplate.get(), vaultPath, key);
    }
    throw new IllegalArgumentException("Unsupported API key path scheme: " + path);
  }
}

/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.oscar.secrets;

import java.util.Map;
import lombok.AllArgsConstructor;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;

@AllArgsConstructor
public class VaultApiKeyProvider implements ApiKeyProvider {
  private static final String DEFAULT_KEY = "value";

  private final VaultTemplate vaultTemplate;
  private final String path;
  private final String key;

  @Override
  public String getKey() {
    VaultResponse response = vaultTemplate.read(path);
    if (response.getData() == null) {
      throw new IllegalStateException("No secret data found at Vault path: " + path);
    }
    Map<String, Object> data = response.getData();
    Object value = data.get(key);
    if (value == null && data.get("data") instanceof Map<?, ?> nested) {
      Object nestedValue = nested.get(key);
      if (nestedValue != null) {
        value = nestedValue;
      }
    }
    if (value == null) {
      throw new IllegalStateException("Missing key '" + key + "' at Vault path: " + path);
    }
    return String.valueOf(value);
  }
}

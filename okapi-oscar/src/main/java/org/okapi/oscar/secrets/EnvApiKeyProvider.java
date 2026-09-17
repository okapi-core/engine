/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.oscar.secrets;

import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EnvApiKeyProvider implements ApiKeyProvider {
  private final String envVar;

  public EnvApiKeyProvider(String envVar) {
    this.envVar = Objects.requireNonNull(envVar, "envVar");
  }

  @Override
  public String getKey() {
    log.info("Looking for var: {}", envVar);
    String value = System.getenv(envVar);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException("Missing key in environment variables: " + envVar);
    }
    return value;
  }
}

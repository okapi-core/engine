/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.oscar.secrets;

import org.springframework.ai.model.ApiKey;

public interface ApiKeyProvider {
  String getKey();

  default ApiKey getSpringAiKey() {
    return new ApiKey() {
      @Override
      public String getValue() {
        return getKey();
      }
    };
  }
}

/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.auth;

import org.okapi.exceptions.UnAuthorizedException;
import org.okapi.fixtures.Deduplicator;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

@SpringBootTest
public class AbstractIT {
  String testInstance = UUID.randomUUID().toString();

  public void setup() throws UnAuthorizedException {}

  public String dedup(String val, Class<?> cls) {
    return Deduplicator.dedup(testInstance, val, cls);
  }
}

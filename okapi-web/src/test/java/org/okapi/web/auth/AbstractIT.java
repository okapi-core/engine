/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.auth;

import java.util.UUID;
import org.okapi.exceptions.UnAuthorizedException;
import org.okapi.fixtures.Deduplicator;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class AbstractIT {
  String testInstance = UUID.randomUUID().toString();

  public void setup() throws UnAuthorizedException {}

  public String dedupWithSession(String val, Class<?> cls) {
    return Deduplicator.dedupWithSession(testInstance, val, cls);
  }
}

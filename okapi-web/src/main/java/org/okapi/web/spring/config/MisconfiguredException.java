/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.spring.config;

public class MisconfiguredException extends RuntimeException {
  public MisconfiguredException(String message) {
    super(message);
  }
}

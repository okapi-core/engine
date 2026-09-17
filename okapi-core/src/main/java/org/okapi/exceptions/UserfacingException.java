/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.exceptions;

public class UserfacingException extends RuntimeException {
  public UserfacingException() {
    super();
  }

  public UserfacingException(String msg) {
    super(msg);
  }
}

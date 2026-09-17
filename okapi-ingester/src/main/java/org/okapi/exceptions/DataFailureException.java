/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.exceptions;

public class DataFailureException extends RuntimeException {
  public DataFailureException() {}

  public DataFailureException(String message) {
    super(message);
  }
}

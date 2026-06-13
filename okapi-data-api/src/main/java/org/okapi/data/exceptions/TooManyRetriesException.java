/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.exceptions;

public class TooManyRetriesException extends Exception {
  public TooManyRetriesException(String message) {
    super(message);
  }
}

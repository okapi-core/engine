/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.exceptions;

// Checked exception to signal a referenced entity is missing.
public class EntityDoesNotExistException extends Exception {
  public EntityDoesNotExistException() {}

  public EntityDoesNotExistException(String message) {
    super(message);
  }
}

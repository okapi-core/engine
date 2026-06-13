/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.exceptions;

public class IllegalJobStateTransition extends Exception {
  public IllegalJobStateTransition(String message) {
    super(message);
  }
}

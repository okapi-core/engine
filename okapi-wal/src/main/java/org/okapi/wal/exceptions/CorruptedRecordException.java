/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.wal.exceptions;

/** Indicates that a WAL frame or LSN could not be parsed due to corruption or truncation. */
public class CorruptedRecordException extends Exception {
  public CorruptedRecordException(String message) {
    super(message);
  }

  public CorruptedRecordException(String message, Throwable cause) {
    super(message, cause);
  }
}

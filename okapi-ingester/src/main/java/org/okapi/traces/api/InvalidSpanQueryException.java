/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.traces.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidSpanQueryException extends RuntimeException {
  public InvalidSpanQueryException(String message) {
    super(message);
  }
}

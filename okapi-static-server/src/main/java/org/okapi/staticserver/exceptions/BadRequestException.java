/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.staticserver.exceptions;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.StandardException;

@StandardException
@AllArgsConstructor
public class BadRequestException extends Exception {
  @Getter int code;
  @Getter String message;
}

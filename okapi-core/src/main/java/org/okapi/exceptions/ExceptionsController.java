/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class ExceptionsController {

  public HttpStatus getHttpError(UserfacingException e) {
    if (e instanceof BadRequestException) {
      return HttpStatus.BAD_REQUEST;
    }
    if (e instanceof UnAuthorizedException) {
      return HttpStatus.UNAUTHORIZED;
    } else {
      return HttpStatus.INTERNAL_SERVER_ERROR;
    }
  }

  @ExceptionHandler(UserfacingException.class)
  public ResponseEntity<String> userException(UserfacingException e) {
    return ResponseEntity.status(getHttpError(e)).body(e.getMessage());
  }
}

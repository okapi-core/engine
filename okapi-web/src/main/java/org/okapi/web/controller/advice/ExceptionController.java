/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.controller.advice;

import java.util.Objects;
import org.okapi.exceptions.BadRequestException;
import org.okapi.exceptions.UnAuthorizedException;
import org.okapi.exceptions.UserfacingException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class ExceptionController {
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

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<String> validationException(MethodArgumentNotValidException e) {
    String message =
        e.getBindingResult().getFieldErrors().stream()
            .map(FieldError::getDefaultMessage)
            .filter(Objects::nonNull)
            .findFirst()
            .orElse("Invalid request");
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(message);
  }
}

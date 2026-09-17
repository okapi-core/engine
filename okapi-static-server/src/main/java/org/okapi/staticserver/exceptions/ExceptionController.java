/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.staticserver.exceptions;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@ControllerAdvice
public class ExceptionController extends ResponseEntityExceptionHandler {
  @ExceptionHandler(BadRequestException.class)
  public final ResponseEntity handleBadRequest(BadRequestException e) {
    var msg = e.getMessage();
    if (msg.equals("")) {
      return ResponseEntity.status(e.getCode()).body("");
    } else return ResponseEntity.status(e.getCode()).body(msg);
  }
}

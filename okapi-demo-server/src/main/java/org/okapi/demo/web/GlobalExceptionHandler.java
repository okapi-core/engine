/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.demo.web;

import io.opentracing.Span;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import org.okapi.demo.rest.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<ErrorResponse> handleResponseStatus(
      ResponseStatusException exception, HttpServletRequest request) {
    traceError(exception);
    log.error("Request {} failed: {}", request.getRequestURI(), exception.getReason(), exception);
    var status = exception.getStatusCode();
    var body = new ErrorResponse(status.value(), exception.getReason(), request.getRequestURI());
    return ResponseEntity.status(status).body(body);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleAll(Exception exception, HttpServletRequest request) {
    traceError(exception);
    log.error(
        "Unexpected error for {}: {}", request.getRequestURI(), exception.getMessage(), exception);
    var status = HttpStatus.INTERNAL_SERVER_ERROR;
    var body = new ErrorResponse(status.value(), "Internal error", request.getRequestURI());
    return ResponseEntity.status(status).body(body);
  }

  private void traceError(Throwable throwable) {
    if (!GlobalTracer.isRegistered()) {
      return;
    }
    Span span = GlobalTracer.get().activeSpan();
    if (span == null) {
      return;
    }
    Tags.ERROR.set(span, true);
    Map<String, Object> fields = new HashMap<>();
    fields.put("event", "error");
    fields.put("error.kind", throwable.getClass().getSimpleName());
    fields.put("message", throwable.getMessage());
    span.log(fields);
  }
}

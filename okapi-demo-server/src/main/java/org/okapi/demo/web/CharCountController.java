/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.demo.web;

import io.opentelemetry.context.Scope;
import jakarta.servlet.http.HttpServletRequest;
import org.okapi.demo.rest.CharCount;
import org.okapi.demo.tracing.TracingHelper;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController("/api/internal")
public class CharCountController {

  private final TracingHelper tracingHelper;

  public CharCountController(TracingHelper tracingHelper) {
    this.tracingHelper = tracingHelper;
  }

  @RequestMapping("/char-count")
  public CharCount charCount(String text, HttpServletRequest request) {
    var span = tracingHelper.startServerSpan(request, "char-count-internal");
    try (Scope ignored = span.makeCurrent()) {
      span.setAttribute("char.length.requested", text == null ? -1 : text.length());
      return new CharCount(text.length());
    } catch (Exception e) {
      tracingHelper.recordError(span, e);
      throw e;
    } finally {
      span.end();
    }
  }
}

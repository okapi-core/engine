/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.demo.web;

import io.opentelemetry.context.Scope;
import jakarta.servlet.http.HttpServletRequest;
import org.okapi.demo.rest.RegexCount;
import org.okapi.demo.rest.RegexCountRequest;
import org.okapi.demo.tracing.TracingHelper;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("/api/internal")
public class RegexCountController {

  private final TracingHelper tracingHelper;

  public RegexCountController(TracingHelper tracingHelper) {
    this.tracingHelper = tracingHelper;
  }

  @PostMapping("/regex-count")
  public RegexCount getRegexCount(
      @RequestBody RegexCountRequest request, HttpServletRequest httpRequest) {
    var span = tracingHelper.startServerSpan(httpRequest, "regex-count-internal");
    try (Scope ignored = span.makeCurrent()) {
      var pattern = java.util.regex.Pattern.compile(request.getRegex());
      var matcher = pattern.matcher(request.getSentence());
      int count = 0;
      while (matcher.find()) {
        count++;
      }
      span.setAttribute("regex.pattern", request.getRegex());
      span.setAttribute("regex.matches", count);
      return new RegexCount(request.getSentence(), request.getRegex(), count);
    } catch (Exception e) {
      tracingHelper.recordError(span, e);
      throw e;
    } finally {
      span.end();
    }
  }
}

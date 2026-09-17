/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.demo.web;

import io.opentelemetry.context.Scope;
import jakarta.servlet.http.HttpServletRequest;
import org.okapi.demo.WordCountService;
import org.okapi.demo.rest.WordCount;
import org.okapi.demo.tracing.TracingHelper;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal")
public class WordCountController {
  private final WordCountService wordCountService;
  private final TracingHelper tracingHelper;

  public WordCountController(WordCountService wordCountService, TracingHelper tracingHelper) {
    this.wordCountService = wordCountService;
    this.tracingHelper = tracingHelper;
  }

  @PostMapping("/word-count")
  public WordCount wordCount(
      @RequestHeader("X-Okapi-Demo-Sentence-Id") String sentenceId,
      @RequestBody String text,
      HttpServletRequest request) {
    var span = tracingHelper.startServerSpan(request, "word-count-internal");
    try (Scope ignored = span.makeCurrent()) {
      span.setAttribute("okapi.sentence_id", sentenceId);
      var result = wordCountService.getCount(sentenceId, text);
      span.setAttribute("okapi.word_count", result.getCount());
      return result;
    } catch (Exception e) {
      tracingHelper.recordError(span, e);
      throw e;
    } finally {
      span.end();
    }
  }
}

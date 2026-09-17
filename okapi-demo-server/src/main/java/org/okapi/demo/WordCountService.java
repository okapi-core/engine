/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.demo;

import com.google.common.base.Preconditions;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import java.util.LinkedHashMap;
import org.okapi.demo.rest.WordCount;
import org.springframework.stereotype.Service;

@Service
public class WordCountService {
  int capacity = 1000;
  LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
  private final Tracer tracer;

  public WordCountService(Tracer tracer) {
    this.tracer = tracer;
  }

  public WordCount getCount(String sentenceId, String text) {
    Span span = tracer.spanBuilder("word-count-service").startSpan();
    try (Scope ignored = span.makeCurrent()) {
      Preconditions.checkNotNull(sentenceId);
      if (counts.containsKey(sentenceId)) {
        span.setAttribute("cache.hit", true);
        return new WordCount(sentenceId, counts.get(sentenceId));
      }
      span.setAttribute("cache.hit", false);
      if (text == null || text.isEmpty()) {
        counts.put(sentenceId, 0);
        span.setAttribute("cache.evicted", counts.size() > capacity);
        return new WordCount(sentenceId, 0);
      }
      if (counts.size() >= capacity) {
        var lastKey = counts.sequencedKeySet().getFirst();
        span.setAttribute("cache.evicted_key", lastKey);
        counts.remove(lastKey);
      }
      String[] words = text.trim().split("\\s+");
      int wordCount = words.length;
      counts.put(sentenceId, wordCount);
      span.setAttribute("word.count", wordCount);
      return new WordCount(sentenceId, wordCount);
    } catch (Exception e) {
      span.recordException(e);
      throw e;
    } finally {
      span.end();
    }
  }
}

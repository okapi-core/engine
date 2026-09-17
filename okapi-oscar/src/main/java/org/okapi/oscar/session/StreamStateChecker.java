/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.oscar.session;

import java.util.function.Supplier;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class StreamStateChecker {

  private static final long INACTIVITY_THRESHOLD_MS = 30_000L;

  private final SessionMetaRepository sessionMetaRepository;

  public Supplier<Boolean> makeSessionInactiveChecker(String sessionId) {
    return () -> {
      var session = sessionMetaRepository.findById(sessionId);
      if (session.isEmpty()) {
        return true;
      }
      long elapsed = System.currentTimeMillis() - session.get().getLastRecordedPing();
      return elapsed > INACTIVITY_THRESHOLD_MS;
    };
  }
}

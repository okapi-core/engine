/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.controller;

import lombok.AllArgsConstructor;
import org.okapi.rest.session.CreateSessionBlindRequest;
import org.okapi.rest.session.ListSessionsBlindRequest;
import org.okapi.rest.session.ListSessionsResponse;
import org.okapi.rest.session.SessionMetaResponse;
import org.okapi.web.service.query.OscarService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/sessions")
@AllArgsConstructor
public class SessionController {

  OscarService oscarService;

  @PostMapping
  public SessionMetaResponse createSession(@RequestBody CreateSessionBlindRequest request) {
    return oscarService.createSession(request);
  }

  @PostMapping("/list")
  public ListSessionsResponse listSessions(@RequestBody ListSessionsBlindRequest request) {
    return oscarService.listSessions(request);
  }

  @GetMapping("/{sessionId}/meta")
  public SessionMetaResponse getSessionMeta(@PathVariable("sessionId") String sessionId) {
    return oscarService.getSessionMeta(sessionId);
  }

  @PostMapping("/{sessionId}/ping")
  public SessionMetaResponse pingSession(@PathVariable("sessionId") String sessionId) {
    return oscarService.pingSession(sessionId);
  }
}

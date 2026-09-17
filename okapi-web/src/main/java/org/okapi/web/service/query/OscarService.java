/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.service.query;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.okapi.oscar.client.OscarClient;
import org.okapi.rest.chat.*;
import org.okapi.rest.session.CreateSessionBlindRequest;
import org.okapi.rest.session.CreateSessionRequest;
import org.okapi.rest.session.ListSessionsBlindRequest;
import org.okapi.rest.session.ListSessionsRequest;
import org.okapi.rest.session.ListSessionsResponse;
import org.okapi.rest.session.SessionMetaResponse;
import org.okapi.web.security.CurrentUserProvider;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class OscarService {

  private final OscarClient oscarClient;
  private final CurrentUserProvider currentUserProvider;

  public ChatResponse postMessage(String sessionId, PostMessageRequest request) {
    return oscarClient.postMessage(sessionId, request);
  }

  public ChatHistoryResponse getHistory(String sessionId, GetHistoryRequest request) {
    return oscarClient.getHistory(sessionId, request);
  }

  public ChatMessageUpdatesResponse getUpdates(String sessionId) {
    return oscarClient.getUpdates(sessionId);
  }

  public ListChatsResponse listChats(ListChatsBlindRequest request) {
    var userId = currentUserProvider.userId();
    var listRequest =
        ListChatsRequest.builder()
            .userId(userId)
            .from(request.getFrom())
            .to(request.getTo())
            .before(request.getBefore())
            .limit(request.getLimit())
            .build();
    return oscarClient.listChats(listRequest);
  }

  public SessionMetaResponse createSession(CreateSessionBlindRequest request) {
    var userId = currentUserProvider.userId();
    var createSessionRequest =
        CreateSessionRequest.builder().ownerId(userId).initialMsg(request.getInitialMsg()).build();
    return oscarClient.createSession(createSessionRequest);
  }

  public ListSessionsResponse listSessions(ListSessionsBlindRequest request) {
    var userId = currentUserProvider.userId();
    var listRequest =
        ListSessionsRequest.builder()
            .userId(userId)
            .from(request.getFrom())
            .to(request.getTo())
            .build();
    return oscarClient.listSessions(listRequest);
  }

  public SessionMetaResponse getSessionMeta(String sessionId) {
    return oscarClient.getSessionMeta(sessionId);
  }

  public SessionMetaResponse pingSession(String sessionId) {
    return oscarClient.pingSession(sessionId);
  }
}

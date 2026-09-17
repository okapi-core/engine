/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.oscar.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.okapi.exceptions.BadRequestException;
import org.okapi.oscar.chat.ChatMessageEntity;
import org.okapi.oscar.chat.ChatMessageRepository;
import org.okapi.oscar.session.SessionMetaEntity;
import org.okapi.oscar.session.SessionMetaRepository;
import org.okapi.rest.chat.CHAT_RESPONSE_TYPE;
import org.okapi.rest.chat.CHAT_ROLE;
import org.okapi.rest.chat.ChatSummaryResponse;
import org.okapi.rest.chat.ListChatsRequest;
import org.okapi.rest.session.SESSION_STATE;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    properties = {"okapi.oscar.chat-list.default-limit=2", "okapi.oscar.chat-list.max-limit=3"})
public class OscarAiListChatsTests {

  @Autowired OscarAi oscarAi;
  @Autowired SessionMetaRepository sessionMetaRepository;
  @Autowired ChatMessageRepository chatMessageRepository;

  String userId;
  String otherUserId;
  String oldestSession;
  String middleSession;
  String newestSession;
  long t0;
  long t1;
  long t2;

  @BeforeEach
  void submitData() {
    userId = UUID.randomUUID().toString();
    otherUserId = UUID.randomUUID().toString();
    oldestSession = UUID.randomUUID().toString();
    middleSession = UUID.randomUUID().toString();
    newestSession = UUID.randomUUID().toString();
    t0 = 1_000_000L;
    t1 = 2_000_000L;
    t2 = 3_000_000L;

    sessionMetaRepository.saveAll(
        List.of(
            session(oldestSession, userId, "oldest", t0),
            session(middleSession, userId, "middle", t1),
            session(newestSession, userId, "newest", t2),
            session(UUID.randomUUID().toString(), otherUserId, "other", t2 + 1)));

    chatMessageRepository.saveAll(
        List.of(
            message(oldestSession, CHAT_ROLE.USER, t0 + 1),
            message(oldestSession, CHAT_ROLE.ASSISTANT, t0 + 2),
            message(middleSession, CHAT_ROLE.USER, t1 + 1),
            message(middleSession, CHAT_ROLE.USER, t1 + 2),
            message(newestSession, CHAT_ROLE.ASSISTANT, t2 + 1),
            message(newestSession, CHAT_ROLE.ASSISTANT, t2 + 2),
            message(newestSession, CHAT_ROLE.USER, t2 + 3)));
  }

  @Test
  void listChatsReturnsSummariesForUserNewestFirst() {
    var response =
        oscarAi.listChats(
            ListChatsRequest.builder().userId(userId).from(t0).to(t2).limit(3).build());

    assertEquals(
        List.of(newestSession, middleSession, oldestSession), sessionIds(response.getChats()));

    var newest = response.getChats().get(0);
    assertEquals("newest", newest.getTitle());
    assertEquals(t2, newest.getCreatedAt());
    assertEquals(1, newest.getMessagesByUser());
    assertEquals(2, newest.getMessagesByAgent());

    var middle = response.getChats().get(1);
    assertEquals(2, middle.getMessagesByUser());
    assertEquals(0, middle.getMessagesByAgent());

    assertNull(response.getNextBefore());
  }

  @Test
  void listChatsUsesConfiguredDefaultLimitAndNextBefore() {
    var firstPage =
        oscarAi.listChats(ListChatsRequest.builder().userId(userId).from(t0).to(t2).build());

    assertEquals(List.of(newestSession, middleSession), sessionIds(firstPage.getChats()));
    assertEquals(t1, firstPage.getNextBefore());

    var secondPage =
        oscarAi.listChats(
            ListChatsRequest.builder()
                .userId(userId)
                .from(t0)
                .to(t2)
                .before(firstPage.getNextBefore())
                .build());

    assertEquals(List.of(oldestSession), sessionIds(secondPage.getChats()));
    assertNull(secondPage.getNextBefore());
  }

  @Test
  void listChatsClampsLimitToConfiguredMax() {
    var response =
        oscarAi.listChats(
            ListChatsRequest.builder().userId(userId).from(t0).to(t2).limit(100).build());

    assertEquals(3, response.getChats().size());
  }

  @Test
  void listChatsReturnsEmptyForUserWithoutChats() {
    var response =
        oscarAi.listChats(
            ListChatsRequest.builder()
                .userId(UUID.randomUUID().toString())
                .from(t0)
                .to(t2)
                .build());

    assertTrue(response.getChats().isEmpty());
    assertNull(response.getNextBefore());
  }

  @Test
  void listChatsHonorsExplicitLimit() {
    var response =
        oscarAi.listChats(
            ListChatsRequest.builder().userId(userId).from(t0).to(t2).limit(1).build());

    assertEquals(List.of(newestSession), sessionIds(response.getChats()));
    assertEquals(t2, response.getNextBefore());
  }

  @Test
  void listChatsReturnsEmptyWhenCursorIsBeforeWindow() {
    var response =
        oscarAi.listChats(
            ListChatsRequest.builder().userId(userId).from(t0).to(t2).before(t0).build());

    assertTrue(response.getChats().isEmpty());
    assertNull(response.getNextBefore());
  }

  @Test
  void listChatsReturnsZeroCountsForChatWithoutMessages() {
    var emptySession = UUID.randomUUID().toString();
    sessionMetaRepository.save(session(emptySession, userId, "empty", t2 + 1));

    var response =
        oscarAi.listChats(
            ListChatsRequest.builder().userId(userId).from(t0).to(t2 + 1).limit(1).build());

    assertEquals(List.of(emptySession), sessionIds(response.getChats()));
    assertEquals(0, response.getChats().get(0).getMessagesByUser());
    assertEquals(0, response.getChats().get(0).getMessagesByAgent());
  }

  @Test
  void listChatsRejectsInvalidTimeRange() {
    assertThrows(
        BadRequestException.class,
        () -> oscarAi.listChats(ListChatsRequest.builder().userId(userId).from(t2).to(t0).build()));
  }

  @Test
  void listChatsRejectsNonPositiveLimit() {
    assertThrows(
        BadRequestException.class,
        () ->
            oscarAi.listChats(
                ListChatsRequest.builder().userId(userId).from(t0).to(t2).limit(0).build()));
  }

  private SessionMetaEntity session(
      String sessionId, String ownerId, String title, long startTime) {
    return SessionMetaEntity.builder()
        .sessionId(sessionId)
        .ownerId(ownerId)
        .sessionTitle(title)
        .state(SESSION_STATE.OPEN)
        .startTime(startTime)
        .lastRecordedPing(startTime)
        .build();
  }

  private ChatMessageEntity message(String sessionId, CHAT_ROLE role, long timestamp) {
    return ChatMessageEntity.builder()
        .sessionId(sessionId)
        .userId(role == CHAT_ROLE.USER ? userId : "assistant")
        .role(role)
        .contents("message")
        .eventStreamId(timestamp)
        .responseType(CHAT_RESPONSE_TYPE.RESPONSE)
        .tsMillis(timestamp)
        .build();
  }

  private List<String> sessionIds(List<ChatSummaryResponse> chats) {
    return chats.stream().map(ChatSummaryResponse::getSessionId).toList();
  }
}

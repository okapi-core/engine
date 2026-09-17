/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.oscar.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.okapi.oscar.chat.ChatMessageEntity;
import org.okapi.oscar.chat.ChatMessageRepository;
import org.okapi.rest.chat.CHAT_RESPONSE_TYPE;
import org.okapi.rest.chat.CHAT_ROLE;
import org.okapi.rest.chat.ChatMessageResponse;
import org.okapi.rest.chat.GetHistoryRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class OscarAiHistoryApiTests {

  @Autowired ChatMessageRepository repository;

  @Autowired OscarAi oscarAi;

  String testSession;
  long testStreamId;
  long t0;
  long t1;
  long t2;

  @BeforeEach
  void submitData() {
    this.testSession = UUID.randomUUID().toString();
    this.testStreamId = 1L;
    t0 = System.currentTimeMillis() - Duration.of(10, ChronoUnit.MINUTES).toMillis();
    t1 = t0 + Duration.of(2, ChronoUnit.MINUTES).toMillis();
    t2 = t1 + Duration.of(2, ChronoUnit.MINUTES).toMillis();
    var msgT0 =
        ChatMessageEntity.builder()
            .sessionId(testSession)
            .userId("user-1")
            .role(CHAT_ROLE.USER)
            .contents("hello")
            .eventStreamId(testStreamId)
            .responseType(CHAT_RESPONSE_TYPE.RESPONSE)
            .tsMillis(t0)
            .build();
    var msgT1 =
        ChatMessageEntity.builder()
            .sessionId(testSession)
            .role(CHAT_ROLE.ASSISTANT)
            .userId("assistant")
            .contents("hello there")
            .eventStreamId(testStreamId)
            .tsMillis(t1)
            .build();
    var msgT2 =
        ChatMessageEntity.builder()
            .sessionId(testSession)
            .role(CHAT_ROLE.ASSISTANT)
            .userId("user-1")
            .contents("bye")
            .eventStreamId(testStreamId)
            .tsMillis(t2)
            .build();
    repository.saveAll(Arrays.asList(msgT0, msgT1, msgT2));
  }

  @Test
  void getHistoryAfter() {
    var history =
        oscarAi.getHistory(
            testSession,
            GetHistoryRequest.builder()
                .from(t0 - Duration.of(1, ChronoUnit.MINUTES).toMillis())
                .build());
    assertEquals(3, history.getResponses().size());
    var msgs = history.getResponses().stream().map(ChatMessageResponse::getContents).toList();
    Assertions.assertEquals(List.of("hello", "hello there", "bye"), msgs);
    var ids =
        history.getResponses().stream().map(ChatMessageResponse::getId).collect(Collectors.toSet());
    assertEquals(3, ids.size());
  }

  @Test
  void getHistoryAfterT1() {
    var history = oscarAi.getHistory(testSession, GetHistoryRequest.builder().from(t1).build());
    assertEquals(2, history.getResponses().size());
    var msgs = history.getResponses().stream().map(ChatMessageResponse::getContents).toList();
    Assertions.assertEquals(List.of("hello there", "bye"), msgs);
    var ids =
        history.getResponses().stream().map(ChatMessageResponse::getId).collect(Collectors.toSet());
    assertEquals(2, ids.size());
  }

  @Test
  void getHistoryFromT0ToJustAfterT1() {
    var history =
        oscarAi.getHistory(
            testSession,
            GetHistoryRequest.builder()
                .from(t0)
                .to(t1 + Duration.of(20, ChronoUnit.SECONDS).toMillis())
                .build());
    assertEquals(2, history.getResponses().size());
    var msgs = history.getResponses().stream().map(ChatMessageResponse::getContents).toList();
    Assertions.assertEquals(List.of("hello", "hello there"), msgs);
    var ids =
        history.getResponses().stream().map(ChatMessageResponse::getId).collect(Collectors.toSet());
    assertEquals(2, ids.size());
  }

  @Test
  void getHistoryReturnsEmptyWhenFromIsAfterAllMessages() {
    var history =
        oscarAi.getHistory(
            testSession,
            GetHistoryRequest.builder()
                .from(t2 + Duration.of(1, ChronoUnit.MINUTES).toMillis())
                .build());
    assertTrue(history.getResponses().isEmpty());
  }

  @Test
  void getHistoryExcludesOtherSessions() {
    var otherSession = UUID.randomUUID().toString();
    repository.save(
        ChatMessageEntity.builder()
            .sessionId(otherSession)
            .userId("user-other")
            .role(CHAT_ROLE.USER)
            .contents("from other session")
            .eventStreamId(99L)
            .responseType(CHAT_RESPONSE_TYPE.RESPONSE)
            .tsMillis(t0)
            .build());

    var history =
        oscarAi.getHistory(
            testSession,
            GetHistoryRequest.builder()
                .from(t0 - Duration.of(1, ChronoUnit.MINUTES).toMillis())
                .build());
    assertEquals(3, history.getResponses().size());
    var msgs = history.getResponses().stream().map(ChatMessageResponse::getContents).toList();
    assertTrue(msgs.stream().noneMatch(c -> c.equals("from other session")));
  }

  @Test
  void getHistoryMapsMetadataCorrectly() {
    var history =
        oscarAi.getHistory(testSession, GetHistoryRequest.builder().from(t0).to(t0).build());
    assertEquals(1, history.getResponses().size());
    var response = history.getResponses().get(0);
    assertEquals("hello", response.getContents());
    assertEquals(CHAT_ROLE.USER, response.getRole());
    assertEquals(CHAT_RESPONSE_TYPE.RESPONSE, response.getResponseType());
    assertEquals(t0, response.getTimestamp());
  }
}

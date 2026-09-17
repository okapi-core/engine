/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.oscar.integ;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.Gson;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.okapi.rest.chat.CHAT_RESPONSE_TYPE;
import org.okapi.rest.chat.ChatMessageResponse;
import org.okapi.rest.chat.ChatMessageUpdatesResponse;
import org.okapi.rest.chat.PostMessageRequest;
import org.okapi.rest.chat.payload.PostResponsePayload;
import org.okapi.rest.session.CreateSessionRequest;
import org.okapi.rest.session.STREAM_STATE;
import org.okapi.rest.session.SessionMetaResponse;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(OscarTestConfig.class)
class SessionFlowIT {

  private static final Gson GSON = new Gson();
  private static final String TEST_USER = "test-user";

  @LocalServerPort int port;

  RestClient restClient;

  @BeforeEach
  void setup() {
    restClient = RestClient.builder().baseUrl("http://localhost:" + port).build();
  }

  @Test
  void fullSessionFlowWithTwoRounds() {
    // Create session via API
    var session =
        restClient
            .post()
            .uri("/api/v1/sessions")
            .body(CreateSessionRequest.builder().initialMsg("hello").ownerId(TEST_USER).build())
            .retrieve()
            .body(SessionMetaResponse.class);
    assertThat(session).isNotNull();
    var sessionId = session.getSessionId();
    awaitFin(sessionId);

    // --- Round 1 ---
    var chatResponse =
        restClient
            .post()
            .uri("/api/v1/chat/{sessionId}", sessionId)
            .contentType(MediaType.APPLICATION_JSON)
            .body(PostMessageRequest.builder().message("First question").userId(TEST_USER).build())
            .retrieve()
            .body(org.okapi.rest.chat.ChatResponse.class);
    assertThat(chatResponse).isNotNull();
    var streamId1 = chatResponse.getStreamId();

    var round1 = pollUntilFin(sessionId);

    assertThat(round1)
        .extracting(ChatMessageResponse::getResponseType)
        .containsExactly(
            CHAT_RESPONSE_TYPE.MARKDOWN_TEXT,
            CHAT_RESPONSE_TYPE.THOUGHT,
            CHAT_RESPONSE_TYPE.PLAN,
            CHAT_RESPONSE_TYPE.RESPONSE);
    assertThat(extractResponse(round1))
        .isEqualTo(org.okapi.oscar.agent.TestOscarAgent.FIRST_CHUNK_RESPONSE);

    // --- Round 2 ---
    var chatResponse2 =
        restClient
            .post()
            .uri("/api/v1/chat/{sessionId}", sessionId)
            .contentType(MediaType.APPLICATION_JSON)
            .body(PostMessageRequest.builder().message("Second question").userId(TEST_USER).build())
            .retrieve()
            .body(org.okapi.rest.chat.ChatResponse.class);
    assertThat(chatResponse2).isNotNull();
    var streamId2 = chatResponse2.getStreamId();
    assertThat(streamId2).isNotEqualTo(streamId1);

    var round2 = pollUntilFin(sessionId);

    assertThat(round2)
        .extracting(ChatMessageResponse::getResponseType)
        .containsExactly(
            CHAT_RESPONSE_TYPE.MARKDOWN_TEXT,
            CHAT_RESPONSE_TYPE.THOUGHT,
            CHAT_RESPONSE_TYPE.RESPONSE);
    assertThat(extractResponse(round2))
        .isEqualTo(org.okapi.oscar.agent.TestOscarAgent.SECOND_CHUNK_RESPONSE);
  }

  private List<ChatMessageResponse> pollUntilFin(String sessionId) {
    var accumulated = new TreeMap<Long, ChatMessageResponse>();
    Awaitility.await()
        .atMost(10, TimeUnit.SECONDS)
        .pollInterval(Duration.ofMillis(200))
        .untilAsserted(
            () -> {
              var updates =
                  restClient
                      .get()
                      .uri("/api/v1/chat/{sessionId}/updates", sessionId)
                      .retrieve()
                      .body(ChatMessageUpdatesResponse.class);
              assertThat(updates).isNotNull();
              updates.getMessages().forEach(m -> accumulated.put(m.getId(), m));
              assertThat(updates.getStreamState()).isEqualTo(STREAM_STATE.FIN);
            });
    return new ArrayList<>(accumulated.values());
  }

  private void awaitFin(String sessionId) {
    Awaitility.await()
        .atMost(10, TimeUnit.SECONDS)
        .pollInterval(Duration.ofMillis(200))
        .untilAsserted(
            () -> {
              var updates =
                  restClient
                      .get()
                      .uri("/api/v1/chat/{sessionId}/updates", sessionId)
                      .retrieve()
                      .body(ChatMessageUpdatesResponse.class);
              assertThat(updates).isNotNull();
              assertThat(updates.getStreamState()).isEqualTo(STREAM_STATE.FIN);
            });
  }

  private String extractResponse(List<ChatMessageResponse> messages) {
    return messages.stream()
        .filter(m -> m.getResponseType() == CHAT_RESPONSE_TYPE.RESPONSE)
        .map(m -> GSON.fromJson(m.getContents(), PostResponsePayload.class).response())
        .findFirst()
        .orElseThrow(() -> new AssertionError("No RESPONSE message found"));
  }
}

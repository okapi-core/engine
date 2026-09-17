/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.oscar;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Duration;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.okapi.oscar.secrets.ApiKeyProvider;
import org.okapi.rest.chat.CHAT_ROLE;
import org.okapi.rest.chat.ChatHistoryResponse;
import org.okapi.rest.chat.GetHistoryRequest;
import org.okapi.rest.session.CreateSessionRequest;
import org.okapi.rest.session.SessionMetaResponse;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.web.client.RestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OkapiOscarSmokeTest {

  private static final MockWebServer OPENAI = new MockWebServer();

  @LocalServerPort int port;

  @TestBean ApiKeyProvider openAiApiKeyProvider;

  static ApiKeyProvider openAiApiKeyProvider() {
    return () -> "fake-openai-key";
  }

  @DynamicPropertySource
  static void configureOpenAi(DynamicPropertyRegistry registry) throws IOException {
    OPENAI.start();
    registry.add("okapi.oscar.openai.base-url", () -> OPENAI.url("/").toString());
  }

  @BeforeEach
  void prepareOpenAiResponses() {
    OPENAI.enqueue(jsonResponse(toolCallResponse()));
    OPENAI.enqueue(jsonResponse(doneResponse()));
  }

  @AfterAll
  static void stopOpenAi() {
    OPENAI.close();
  }

  @Test
  void createsSessionAndReturnsUserAndAssistantHistory() throws InterruptedException {
    var client = RestClient.builder().baseUrl("http://localhost:" + port).build();
    var session =
        client
            .post()
            .uri("/api/v1/sessions")
            .body(
                CreateSessionRequest.builder()
                    .ownerId("smoke-test-user")
                    .initialMsg("hello")
                    .build())
            .retrieve()
            .body(SessionMetaResponse.class);

    assertThat(session).isNotNull();
    assertThat(session.getSessionId()).isNotBlank();

    Awaitility.await()
        .atMost(Duration.ofSeconds(5))
        .pollInterval(Duration.ofMillis(500))
        .untilAsserted(
            () -> {
              var history =
                  client
                      .post()
                      .uri("/api/v1/chat/history/{sessionId}", session.getSessionId())
                      .body(GetHistoryRequest.fromStart())
                      .retrieve()
                      .body(ChatHistoryResponse.class);

              assertThat(history).isNotNull();
              assertThat(history.getResponses())
                  .anySatisfy(
                      message -> {
                        assertThat(message.getRole()).isEqualTo(CHAT_ROLE.USER);
                        assertThat(message.getContents()).isEqualTo("hello");
                      })
                  .anySatisfy(
                      message -> {
                        assertThat(message.getRole()).isEqualTo(CHAT_ROLE.ASSISTANT);
                        assertThat(message.getContents())
                            .isEqualTo("{\"response\":\"Hello from fake OpenAI\"}");
                      });
            });

    Awaitility.await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> assertThat(OPENAI.getRequestCount()).isEqualTo(2));

    var toolCallRequest = OPENAI.takeRequest();
    var doneRequest = OPENAI.takeRequest();
    assertThat(toolCallRequest.getUrl().encodedPath()).isEqualTo("/v1/chat/completions");
    assertThat(doneRequest.getUrl().encodedPath()).isEqualTo("/v1/chat/completions");
    assertThat(toolCallRequest.getHeaders().get("Authorization"))
        .isEqualTo("Bearer fake-openai-key");
  }

  private static MockResponse jsonResponse(String body) {
    return new MockResponse.Builder()
        .code(200)
        .addHeader("Content-Type", "application/json")
        .body(body)
        .build();
  }

  private static String toolCallResponse() {
    return """
        {
          "id": "chatcmpl-test-1",
          "object": "chat.completion",
          "created": 1,
          "model": "gpt-5.5",
          "choices": [{
            "index": 0,
            "message": {
              "role": "assistant",
              "content": null,
              "tool_calls": [{
                "id": "call-test-1",
                "type": "function",
                "function": {
                  "name": "postResponse",
                  "arguments": "{\\"response\\":\\"Hello from fake OpenAI\\"}"
                }
              }]
            },
            "finish_reason": "tool_calls"
          }]
        }
        """;
  }

  private static String doneResponse() {
    return """
        {
          "id": "chatcmpl-test-2",
          "object": "chat.completion",
          "created": 2,
          "model": "gpt-5.5",
          "choices": [{
            "index": 0,
            "message": {
              "role": "assistant",
              "content": "DONE"
            },
            "finish_reason": "stop"
          }]
        }
        """;
  }
}

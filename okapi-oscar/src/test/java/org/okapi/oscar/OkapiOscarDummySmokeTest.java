/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.oscar;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.okapi.rest.chat.CHAT_ROLE;
import org.okapi.rest.chat.ChatHistoryResponse;
import org.okapi.rest.chat.GetHistoryRequest;
import org.okapi.rest.session.CreateSessionRequest;
import org.okapi.rest.session.SessionMetaResponse;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dummy")
class OkapiOscarDummySmokeTest {

  @LocalServerPort int port;

  @Test
  void createsSessionAndReturnsUserAndAssistantHistory() {
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
        .atMost(Duration.ofSeconds(10))
        .pollInterval(Duration.ofMillis(200))
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
                      message -> assertThat(message.getRole()).isEqualTo(CHAT_ROLE.ASSISTANT));
            });
  }
}

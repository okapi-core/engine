/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.oscar.integ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.okapi.oscar.integ.OtelHelpers.kv;
import static org.okapi.oscar.integ.OtelHelpers.resourceSpans;
import static org.okapi.oscar.integ.OtelHelpers.spanId;
import static org.okapi.oscar.integ.OtelHelpers.traceId;
import static org.okapi.oscar.integ.OtelHelpers.traceRequest;

import com.google.gson.Gson;
import io.opentelemetry.proto.trace.v1.Span;
import io.opentelemetry.proto.trace.v1.Status;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.okapi.bytes.OkapiBytes;
import org.okapi.ingester.client.IngesterClient;
import org.okapi.rest.chat.CHAT_RESPONSE_TYPE;
import org.okapi.rest.chat.ChatMessageResponse;
import org.okapi.rest.chat.ChatMessageUpdatesResponse;
import org.okapi.rest.chat.payload.GetTraceFollowUpPayload;
import org.okapi.rest.chat.payload.PostResponsePayload;
import org.okapi.rest.chat.payload.PostToolCallRequestPayload;
import org.okapi.rest.chat.payload.PostToolCallResponsePayload;
import org.okapi.rest.session.CreateSessionRequest;
import org.okapi.rest.session.STREAM_STATE;
import org.okapi.rest.session.SessionMetaResponse;
import org.okapi.rest.traces.SpanQueryV2Request;
import org.okapi.rest.traces.TimestampFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dummy")
class DummyToolCallFlowIT {

  private static final Gson GSON = new Gson();
  private static final String TEST_USER = "test-user";

  @LocalServerPort int port;

  @Autowired IngesterClient ingesterClient;

  RestClient restClient;

  @BeforeEach
  void setup() {
    restClient = RestClient.builder().baseUrl("http://localhost:" + port).build();
  }

  @Test
  void fetchTraceFlowEmitsToolCallMessagesInOrder() {
    var traceIdBytes = traceId();
    var traceIdHex = OkapiBytes.encodeAsHex(traceIdBytes.toByteArray());
    long startNs = System.currentTimeMillis() * 1_000_000L;
    var span =
        Span.newBuilder()
            .setTraceId(traceIdBytes)
            .setSpanId(spanId())
            .setName("GET /checkout")
            .setKind(Span.SpanKind.SPAN_KIND_SERVER)
            .setStartTimeUnixNano(startNs)
            .setEndTimeUnixNano(startNs + 10_000_000L)
            .addAttributes(kv("http.status_code", 200))
            .setStatus(Status.newBuilder().setCode(Status.StatusCode.STATUS_CODE_OK).build())
            .build();
    ingesterClient.ingestOtelTraces(traceRequest(resourceSpans("checkout", List.of(span))));
    awaitTraceAvailable(traceIdHex, startNs);

    var session =
        restClient
            .post()
            .uri("/api/v1/sessions")
            .body(
                CreateSessionRequest.builder()
                    .initialMsg("Fetch trace : " + traceIdHex)
                    .ownerId(TEST_USER)
                    .build())
            .retrieve()
            .body(SessionMetaResponse.class);
    assertThat(session).isNotNull();

    var sessionId = session.getSessionId();
    var messages = pollUntilFin(sessionId);

    assertThat(messages).allMatch(msg -> msg.getResponseType() != null);
    assertThat(messages)
        .extracting(ChatMessageResponse::getResponseType)
        .containsExactly(
            CHAT_RESPONSE_TYPE.MARKDOWN_TEXT,
            CHAT_RESPONSE_TYPE.TOOL_CALL_REQUEST,
            CHAT_RESPONSE_TYPE.TOOL_CALL_RESPONSE,
            CHAT_RESPONSE_TYPE.GET_TRACE_FOLLOW_UP,
            CHAT_RESPONSE_TYPE.RESPONSE);

    var toolRequest =
        GSON.fromJson(messages.get(1).getContents(), PostToolCallRequestPayload.class);
    assertThat(toolRequest.getSummary()).isNotBlank();

    var toolResponse =
        GSON.fromJson(messages.get(2).getContents(), PostToolCallResponsePayload.class);
    assertThat(toolResponse.getSummary()).isNotBlank();

    var followUp = GSON.fromJson(messages.get(3).getContents(), GetTraceFollowUpPayload.class);
    assertThat(followUp.traceId()).isEqualTo(traceIdHex);

    var response = GSON.fromJson(messages.get(4).getContents(), PostResponsePayload.class);
    assertThat(response.response()).contains("Found");
  }

  private void awaitTraceAvailable(String traceIdHex, long startNs) {
    Awaitility.await()
        .atMost(15, TimeUnit.SECONDS)
        .pollInterval(Duration.ofMillis(300))
        .until(
            () -> {
              var resp =
                  ingesterClient.querySpans(
                      SpanQueryV2Request.builder()
                          .traceId(traceIdHex)
                          .timestampFilter(
                              TimestampFilter.builder()
                                  .tsStartNanos(startNs - 1_000_000_000L)
                                  .tsEndNanos(startNs + 1_000_000_000L)
                                  .build())
                          .build());
              return resp.getItems() != null && !resp.getItems().isEmpty();
            });
  }

  private List<ChatMessageResponse> pollUntilFin(String sessionId) {
    var accumulated = new java.util.TreeMap<Long, ChatMessageResponse>();
    Awaitility.await()
        .atMost(30, TimeUnit.SECONDS)
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
              for (var msg : updates.getMessages()) {
                accumulated.put(msg.getId(), msg);
              }
              assertThat(updates.getStreamState()).isEqualTo(STREAM_STATE.FIN);
            });
    return accumulated.values().stream().toList();
  }
}

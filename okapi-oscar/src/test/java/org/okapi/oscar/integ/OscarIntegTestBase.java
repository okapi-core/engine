/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.oscar.integ;

import static org.awaitility.Awaitility.await;

import com.google.gson.Gson;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.TestInstance;
import org.okapi.ingester.client.IngesterClient;
import org.okapi.oscar.integ.judge.JudgeAgent;
import org.okapi.oscar.service.OscarAi;
import org.okapi.rest.chat.CHAT_RESPONSE_TYPE;
import org.okapi.rest.chat.ChatMessageResponse;
import org.okapi.rest.chat.GetHistoryRequest;
import org.okapi.rest.chat.PostMessageRequest;
import org.okapi.rest.chat.payload.PostResponsePayload;
import org.okapi.rest.session.CreateSessionRequest;
import org.okapi.rest.session.STREAM_STATE;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ActiveProfiles("test")
public abstract class OscarIntegTestBase {

  protected static final Logger log = LoggerFactory.getLogger(OscarIntegTestBase.class);
  protected static final String TEST_USER = "test-user";
  private static final Gson GSON = new Gson();

  @Autowired protected OscarAi oscarAi;
  @Autowired protected JudgeAgent judgeAgent;
  @Autowired protected IngesterClient ingesterClient;

  protected String session(String initial) {
    var request = CreateSessionRequest.builder().ownerId(TEST_USER).initialMsg(initial).build();
    return oscarAi.createSession(request).getSessionId();
  }

  protected String pollUntilFinAndReturnResponse(String sessionId) {
    var gson = new Gson();
    await()
        .atMost(30, TimeUnit.MINUTES)
        .pollDelay(10, TimeUnit.SECONDS)
        .until(
            () -> {
              var updates = oscarAi.getUpdates(sessionId);
              return updates.getStreamState() == STREAM_STATE.FIN;
            });
    var history = oscarAi.getHistory(sessionId, GetHistoryRequest.fromStart());
    var maybeResponse =
        history.getResponses().stream()
            .filter(res -> res.getResponseType() == CHAT_RESPONSE_TYPE.RESPONSE)
            .findFirst();
    if (maybeResponse.isEmpty()) {
      throw new IllegalStateException("No response found, something went wrong with the agent.");
    }
    var parsed = gson.fromJson(maybeResponse.get().getContents(), PostResponsePayload.class);
    return parsed.response();
  }

  protected PostMessageRequest msg(String message) {
    return PostMessageRequest.builder().message(message).userId(TEST_USER).build();
  }

  protected String getLatest(String sessionId) {
    List<ChatMessageResponse> responses = oscarAi.getHistory(sessionId, 0L, null).getResponses();
    ChatMessageResponse latest =
        responses.stream()
            .filter(r -> r.getResponseType() == CHAT_RESPONSE_TYPE.RESPONSE)
            .reduce((a, b) -> b)
            .orElseThrow(
                () ->
                    new IllegalStateException("No RESPONSE message found in session " + sessionId));
    String responseText = GSON.fromJson(latest.getContents(), PostResponsePayload.class).response();
    log.info("[session={}] Latest AI response:\n{}", sessionId, responseText);
    return responseText;
  }
}

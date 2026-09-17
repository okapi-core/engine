/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.oscar.agent;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import lombok.AllArgsConstructor;
import org.okapi.oscar.session.StreamMetaRepository;
import org.okapi.oscar.tools.StatefulToolFactory;
import org.okapi.oscar.tools.StatefulTools;
import org.okapi.rest.session.STREAM_STATE;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class TestOscarAgent {

  static final List<String> FIRST_CHUNK_THOUGHTS = List.of("Analyzing the first request");
  static final String FIRST_CHUNK_PLAN = "Step 1: retrieve data. Step 2: format response.";
  public static final String FIRST_CHUNK_RESPONSE = "First response complete.";

  static final List<String> SECOND_CHUNK_THOUGHTS = List.of("Analyzing the second request");
  public static final String SECOND_CHUNK_RESPONSE = "Second response complete.";

  private final StatefulToolFactory statefulToolFactory;
  private final StreamMetaRepository streamMetaRepository;

  public void submitFirstChunk(String sessionId, String streamId) {
    CompletableFuture.runAsync(
        () -> runChunk(sessionId, Long.parseLong(streamId), this::writeFirstChunk));
  }

  public void submitSecondChunk(String sessionId, String streamId) {
    CompletableFuture.runAsync(
        () -> runChunk(sessionId, Long.parseLong(streamId), this::writeSecondChunk));
  }

  private void runChunk(String sessionId, long streamId, ToolWriter writer) {
    try {
      var tools = statefulToolFactory.getTools(sessionId, streamId).getStatefulTools();
      writer.write(tools);
      streamMetaRepository.updateState(streamId, STREAM_STATE.FIN);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("TestOscarAgent interrupted", e);
    }
  }

  private void writeFirstChunk(StatefulTools tools) throws InterruptedException {
    for (String thought : FIRST_CHUNK_THOUGHTS) {
      tools.postThought(thought);
      Thread.sleep(100);
    }
    tools.postPlan(FIRST_CHUNK_PLAN);
    Thread.sleep(100);
    tools.postResponse(FIRST_CHUNK_RESPONSE);
  }

  private void writeSecondChunk(StatefulTools tools) throws InterruptedException {
    for (String thought : SECOND_CHUNK_THOUGHTS) {
      tools.postThought(thought);
      Thread.sleep(100);
    }
    tools.postResponse(SECOND_CHUNK_RESPONSE);
  }

  @FunctionalInterface
  private interface ToolWriter {
    void write(StatefulTools tools) throws InterruptedException;
  }
}

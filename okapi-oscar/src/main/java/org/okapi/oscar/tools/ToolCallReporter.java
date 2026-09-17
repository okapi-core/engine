/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.oscar.tools;

import com.google.gson.Gson;
import lombok.Getter;

public class ToolCallReporter {

  private static final Gson GSON = new Gson();

  @Getter private final String sessionId;
  @Getter private final long streamId;
  private final StatefulTools statefulTools;

  public ToolCallReporter(String sessionId, long streamId, StatefulTools statefulTools) {
    this.sessionId = sessionId;
    this.streamId = streamId;
    this.statefulTools = statefulTools;
  }

  public void reportRequest(String toolName, Object request, String summary) {
    String json = request == null ? "null" : GSON.toJson(request);
    statefulTools.postToolCallRequest(toolName, json, summary);
  }

  public void reportResponse(String toolName, String summary) {
    statefulTools.postToolCallResponse(toolName, summary);
  }

  public void reportResponseSummaryOnly(String toolName, String summary) {
    statefulTools.postToolCallResponse(toolName, summary);
  }
}

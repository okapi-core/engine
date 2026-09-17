/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.oscar.controller;

import org.okapi.oscar.service.OscarAi;
import org.okapi.rest.chat.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

  @Autowired OscarAi oscarAi;

  @PostMapping("/{sessionId}")
  public ChatResponse postMsg(
      @PathVariable("sessionId") String sessionId,
      @RequestBody @Validated PostMessageRequest request) {
    return oscarAi.postMessage(sessionId, request);
  }

  @PostMapping("/history/{sessionId}")
  public ChatHistoryResponse getHistory(
      @PathVariable("sessionId") String sessionId,
      @RequestBody @Validated GetHistoryRequest request) {
    return oscarAi.getHistory(sessionId, request);
  }

  @GetMapping("/{sessionId}/updates")
  public ChatMessageUpdatesResponse getUpdates(@PathVariable("sessionId") String sessionId) {
    return oscarAi.getUpdates(sessionId);
  }

  @PostMapping("/list")
  public ListChatsResponse listChats(@RequestBody @Validated ListChatsRequest request) {
    return oscarAi.listChats(request);
  }
}

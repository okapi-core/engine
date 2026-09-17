/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.oscar.service;

import org.okapi.oscar.chat.ChatMessageEntity;
import org.okapi.rest.chat.ChatMessageResponse;

public class DtoMappers {
  public static ChatMessageResponse mapChatEntity(ChatMessageEntity m) {
    return ChatMessageResponse.builder()
        .id(m.getId())
        .timestamp(m.getTsMillis())
        .contents(m.getContents())
        .eventStreamId(m.getEventStreamId())
        .responseType(m.getResponseType())
        .role(m.getRole())
        .build();
  }
}

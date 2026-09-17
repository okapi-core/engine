/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.oscar.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.okapi.oscar.chat.ChatMessageEntity;
import org.okapi.rest.chat.CHAT_RESPONSE_TYPE;
import org.okapi.rest.chat.CHAT_ROLE;
import org.okapi.rest.chat.ChatMessageResponse;

public class DtoMappersTest {

  @Test
  void mapsAllFieldsFromEntity() {
    var entity =
        ChatMessageEntity.builder()
            .id(100L)
            .sessionId("session-1")
            .userId("user-1")
            .role(CHAT_ROLE.USER)
            .contents("hello")
            .eventStreamId(42L)
            .responseType(CHAT_RESPONSE_TYPE.RESPONSE)
            .tsMillis(12345L)
            .build();

    ChatMessageResponse response = DtoMappers.mapChatEntity(entity);

    assertEquals("hello", response.getContents());
    assertEquals(42L, response.getEventStreamId());
    assertEquals(CHAT_ROLE.USER, response.getRole());
    assertEquals(CHAT_RESPONSE_TYPE.RESPONSE, response.getResponseType());
    assertEquals(12345L, response.getTimestamp());
  }

  @Test
  void mapsNullResponseType() {
    var entity =
        ChatMessageEntity.builder()
            .id(100L)
            .sessionId("session-1")
            .userId("user-1")
            .role(CHAT_ROLE.ASSISTANT)
            .contents("hello there")
            .eventStreamId(42L)
            .tsMillis(99999L)
            .build();

    ChatMessageResponse response = DtoMappers.mapChatEntity(entity);

    assertEquals(CHAT_ROLE.ASSISTANT, response.getRole());
    assertEquals("hello there", response.getContents());
    assertEquals(99999L, response.getTimestamp());
    assertNull(response.getResponseType());
  }
}

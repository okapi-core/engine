/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.chat;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class ChatMessageResponse {
  @NotNull long id;
  @NotNull long timestamp;
  @NotNull String contents;
  @NotNull long eventStreamId;
  CHAT_RESPONSE_TYPE responseType;
  @NotNull CHAT_ROLE role;
}

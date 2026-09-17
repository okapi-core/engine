/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.chat;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.okapi.rest.session.STREAM_STATE;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class ChatMessageUpdatesResponse {
  List<ChatMessageResponse> messages;
  STREAM_STATE streamState;
}

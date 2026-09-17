/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
public class ChatSummaryResponse {
  String sessionId;
  String title;
  long createdAt;
  long messagesByUser;
  long messagesByAgent;
}

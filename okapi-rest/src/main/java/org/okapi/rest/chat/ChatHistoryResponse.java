/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.chat;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.*;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@ToString
public class ChatHistoryResponse {
  @NotNull List<ChatMessageResponse> responses;
}

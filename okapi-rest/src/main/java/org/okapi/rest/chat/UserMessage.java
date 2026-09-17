/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class UserMessage {
  String timestamp;
  String content;
}

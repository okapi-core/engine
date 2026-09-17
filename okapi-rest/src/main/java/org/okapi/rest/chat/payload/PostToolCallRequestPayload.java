/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.chat.payload;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.okapi.rest.annotations.TsResponseType;

@AllArgsConstructor
@Getter
@TsResponseType
public class PostToolCallRequestPayload {
  private final String toolName;
  private final String requestJson;
  private final String summary;
}

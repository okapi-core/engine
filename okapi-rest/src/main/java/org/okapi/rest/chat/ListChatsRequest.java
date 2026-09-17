/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
public class ListChatsRequest {
  @NotNull @NotBlank String userId;
  Long from;
  Long to;
  Long before;
  Integer limit;
}

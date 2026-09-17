/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.session;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ListSessionsBlindRequest {
  long from;
  long to;
}

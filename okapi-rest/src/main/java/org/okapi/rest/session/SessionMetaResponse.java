/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.session;

import lombok.*;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@ToString
public class SessionMetaResponse {
  String sessionId;
  Long ongoingStreamId;
  long lastRecordedPing;
  SESSION_STATE state;
}

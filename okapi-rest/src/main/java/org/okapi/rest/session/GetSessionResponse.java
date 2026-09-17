/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.session;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
public class GetSessionResponse {
  long timestamp;
  String title;
  STREAM_STATE state;
}

/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.dtos.logs;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Builder
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class LogsViewLogDto {
  String appId;
  Instant lastViewedAt;
}

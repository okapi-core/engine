/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.dtos.traces;

import java.time.Instant;

public class TraceViewLogWDto {
  String appId;
  String traceId;
  Instant lastViewedAt;
}

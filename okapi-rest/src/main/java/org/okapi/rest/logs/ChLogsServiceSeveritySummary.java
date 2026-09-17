/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.logs;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@AllArgsConstructor
@Builder
@Getter
@NoArgsConstructor
@ToString
public class ChLogsServiceSeveritySummary {
  String serviceName;
  long total;
  long trace;
  long debug;
  long info;
  long warn;
  long error;
  long fatal;
  double errorRatio;
}

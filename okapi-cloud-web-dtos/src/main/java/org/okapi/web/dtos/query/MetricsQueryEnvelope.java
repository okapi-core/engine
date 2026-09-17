/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.dtos.query;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
public class MetricsQueryEnvelope {
  MetricsQueryRequest query;
}

/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.metrics.search;

import lombok.*;

@Builder
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Getter
public class SubmitMetricsResponse {
  String message;
}

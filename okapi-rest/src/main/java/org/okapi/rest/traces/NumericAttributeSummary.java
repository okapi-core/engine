/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.traces;

import lombok.*;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class NumericAttributeSummary {
  Double avg;
  Double p25;
  Double p50;
  Double p75;
  Double p90;
}

/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.metrics.payloads;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
public class ExponentialHisto {
  List<ExponentialHistoPoint> histoPoints;
}

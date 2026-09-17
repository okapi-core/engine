/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.traces;

import lombok.*;
import org.okapi.metrics.pojos.AGG_TYPE;
import org.okapi.metrics.pojos.RES_TYPE;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class NumericalAggConfig {
  AGG_TYPE aggregation;
  RES_TYPE resType;
}

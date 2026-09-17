/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.metrics.pojos.results;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@AllArgsConstructor
@Builder
@Getter
public class SumScan extends Scan {
  String universalPath;
  List<Long> ts;
  long windowSize;
  List<Double> counts;
}

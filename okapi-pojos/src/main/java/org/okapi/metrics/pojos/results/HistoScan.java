/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.metrics.pojos.results;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor
@Value
public class HistoScan extends Scan {
  String universalPath;
  long start;
  long end;
  List<Float> ubs;
  List<Integer> counts;
}

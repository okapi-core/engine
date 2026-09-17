/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.metrics.ch;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.okapi.ch.AbstractChRow;

@AllArgsConstructor
@Getter
@Builder
public class ChGaugeSampleRow extends AbstractChRow {
  long timestamp;
  String metric;
  Map<String, String> tags;
  double value;
  String unit;
}

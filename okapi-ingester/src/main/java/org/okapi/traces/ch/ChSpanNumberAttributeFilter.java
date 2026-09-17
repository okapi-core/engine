/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.traces.ch;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ChSpanNumberAttributeFilter {
  String key;
  int bucket;
  Double value;
}

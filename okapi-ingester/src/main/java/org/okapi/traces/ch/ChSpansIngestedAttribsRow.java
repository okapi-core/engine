/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.traces.ch;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ChSpansIngestedAttribsRow {
  long ts_start_ns;
  long ts_end_ns;
  String attribute_name;
  String attribute_type;
}

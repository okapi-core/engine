/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.traces.ch;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ChSpanAttributeValuesCustomTemplate {
  String table;
  String attributeName;
  int bucket;
  String valueExpr;
  Integer limit;
  Long tsStartNs;
  Long tsEndNs;
}

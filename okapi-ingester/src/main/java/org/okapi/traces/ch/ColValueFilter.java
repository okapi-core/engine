/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.traces.ch;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ColValueFilter {
  String colName;
  String strValue;
  Double doubleValue;
  Integer intValue;
}

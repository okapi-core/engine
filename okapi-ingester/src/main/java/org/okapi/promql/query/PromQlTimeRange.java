/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.query;

import lombok.Value;

@Value
public class PromQlTimeRange {
  long startMs;
  long endMs;
}

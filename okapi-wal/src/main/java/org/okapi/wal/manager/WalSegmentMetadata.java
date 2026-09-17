/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.wal.manager;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@AllArgsConstructor
@Getter
@Builder
public class WalSegmentMetadata {
  int segmentNumber;
  long largestLsn;
}

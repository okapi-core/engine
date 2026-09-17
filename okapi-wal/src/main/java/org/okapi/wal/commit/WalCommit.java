/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.wal.commit;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.okapi.wal.lsn.Lsn;

@AllArgsConstructor
@Getter
public class WalCommit {
  private final Lsn lsn;
}

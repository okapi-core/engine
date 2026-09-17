/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.wal.factory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.okapi.wal.LsnSupplier;
import org.okapi.wal.io.WalReader;
import org.okapi.wal.io.WalWriter;
import org.okapi.wal.manager.WalManager;

@AllArgsConstructor
@Builder
@Getter
public class WalResourceBundle {
  WalReader reader;
  WalWriter writer;
  WalManager manager;
  LsnSupplier lsnSupplier;
}

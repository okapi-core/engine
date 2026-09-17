/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.wal.lsn;

import java.util.concurrent.atomic.AtomicLong;
import org.okapi.wal.LsnSupplier;

public class MonoticLsnSupplier implements LsnSupplier {
  AtomicLong lsnSupplier;

  public MonoticLsnSupplier(long startingPoint) {
    lsnSupplier = new AtomicLong(startingPoint);
  }

  @Override
  public Lsn getLsn() {
    var num = lsnSupplier.incrementAndGet();
    return Lsn.fromNumber(num);
  }

  @Override
  public Lsn next() {
    return getLsn();
  }
}

/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.logs.ch;

import org.okapi.runtime.ch.ChWalConsumerDriver;

public class ChLogsWalConsumerDriver implements ChWalConsumerDriver {
  private final ChLogsWalConsumer walConsumer;

  public ChLogsWalConsumerDriver(ChLogsWalConsumer walConsumer) {
    this.walConsumer = walConsumer;
  }

  @Override
  public void onTick() {
    try {
      walConsumer.consumeRecords();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}

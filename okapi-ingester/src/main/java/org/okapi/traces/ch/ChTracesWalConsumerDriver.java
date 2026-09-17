/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.traces.ch;

import org.okapi.runtime.ch.ChWalConsumerDriver;

public class ChTracesWalConsumerDriver implements ChWalConsumerDriver {
  private final ChTracesWalConsumer walConsumer;

  public ChTracesWalConsumerDriver(ChTracesWalConsumer walConsumer) {
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

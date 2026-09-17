/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.metrics.ch;

import org.okapi.runtime.ch.ChWalConsumerDriver;

public class ChMetricsWalConsumerDriver implements ChWalConsumerDriver {
  private final ChMetricsWalConsumer walConsumer;

  public ChMetricsWalConsumerDriver(ChMetricsWalConsumer walConsumer) {
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

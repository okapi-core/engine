/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.runtime.ch;

import java.util.List;

public class ChWalConsumerCommonDriver {
  private final List<ChWalConsumerDriver> drivers;

  public ChWalConsumerCommonDriver(List<ChWalConsumerDriver> drivers) {
    this.drivers = drivers;
  }

  public void onTick() {
    for (var driver : drivers) {
      driver.onTick();
    }
  }
}

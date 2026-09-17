/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi;

public interface Statistics {
  float percentile(double quantile);

  float avg();

  float min();

  float max();

  byte[] serialize();

  float getSum();

  float getCount();

  float getSumOfDeviationsSquared();
}

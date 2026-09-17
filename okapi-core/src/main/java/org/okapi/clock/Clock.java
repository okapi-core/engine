/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.clock;

public interface Clock {
  long currentTimeMillis();

  long getTime();
}

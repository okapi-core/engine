/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.metrics.pojos.results;

import java.util.List;

/** Explicit empty result for a series identity that has no persisted metadata. */
public final class MissingScan extends GaugeScan {
  public static final MissingScan INSTANCE = new MissingScan();

  private MissingScan() {
    super("", List.of(), List.of());
  }
}

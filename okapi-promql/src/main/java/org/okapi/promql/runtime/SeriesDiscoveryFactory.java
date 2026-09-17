/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.runtime;

import org.okapi.promql.eval.ts.SeriesDiscovery;

public interface SeriesDiscoveryFactory {
  SeriesDiscovery get();
}

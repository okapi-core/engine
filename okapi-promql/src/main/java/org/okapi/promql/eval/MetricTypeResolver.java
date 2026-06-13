/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.eval;

import org.okapi.promql.eval.VectorData.SeriesId;

public interface MetricTypeResolver {
  boolean isCounter(SeriesId id);
}

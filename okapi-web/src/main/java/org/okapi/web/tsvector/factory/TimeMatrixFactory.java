/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.tsvector.factory;

import org.okapi.web.tsvector.LegendFn;
import org.okapi.web.tsvector.TimeMatrix;

public interface TimeMatrixFactory<LegendIn, T> {
  TimeMatrix createTimeMatrix(LegendFn<LegendIn> legendFn, T base);
}

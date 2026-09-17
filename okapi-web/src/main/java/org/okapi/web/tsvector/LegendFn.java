/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.tsvector;

public interface LegendFn<T> {
  String getLegend(T base);
}

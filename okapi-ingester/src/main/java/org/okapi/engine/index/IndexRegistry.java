/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.engine.index;

public interface IndexRegistry {
  IndexConfig getIndexConfig(String indexId);
}

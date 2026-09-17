/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.metrics.core;

import java.io.IOException;
import java.util.List;

public interface MetricsEventEmitter {
  void commit() throws IOException;

  List<MetricEvent> next(int batchSize) throws IOException;
}

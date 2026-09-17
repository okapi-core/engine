/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.logs.core;

import java.io.IOException;
import java.util.List;

public interface LogsEventEmitter {
  void commit() throws IOException;

  List<LogsEvent> next(int batchSize) throws IOException;
}

/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.traces.core;

import java.io.IOException;
import java.util.List;

public interface TracesEventEmitter {
  void commit() throws IOException;

  List<TracesEvent> next(int batchSize) throws IOException;
}

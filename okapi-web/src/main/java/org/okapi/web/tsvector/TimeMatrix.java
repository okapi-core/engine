/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.tsvector;

import java.util.List;

public interface TimeMatrix {
  TimeVector getTimeVector(String path);

  int size();

  List<String> getPaths();
}

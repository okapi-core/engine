/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.tsvector;

import java.util.List;
import java.util.Optional;

public interface TimeVector {
  Optional<Float> at(long timestamp);

  TimeVector slice(long startTimestamp, long endTimestamp);

  List<Float> values();

  List<Long> timestamps();
}

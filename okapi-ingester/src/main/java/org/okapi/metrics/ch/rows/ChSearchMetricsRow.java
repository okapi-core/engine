/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.metrics.ch.rows;

import java.util.Map;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.okapi.rest.metrics.query.METRIC_TYPE;

@AllArgsConstructor
@Builder
@Getter
public class ChSearchMetricsRow {
  String name;
  Map<String, String> tags;
  METRIC_TYPE metricType;
  String temporality;

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    ChSearchMetricsRow that = (ChSearchMetricsRow) o;
    return Objects.equals(name, that.name)
        && Objects.equals(tags, that.tags)
        && metricType == that.metricType
        && Objects.equals(temporality, that.temporality);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, tags, metricType, temporality);
  }
}

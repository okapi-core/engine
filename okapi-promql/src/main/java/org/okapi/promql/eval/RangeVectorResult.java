/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.eval;

import java.util.*;
import lombok.ToString;
import org.okapi.promql.eval.VectorData.*;

@ToString
public final class RangeVectorResult implements ExpressionResult, Iterable<SeriesWindow> {
  private final List<SeriesWindow> data;

  public RangeVectorResult(List<SeriesWindow> data) {
    this.data = data;
  }

  public List<SeriesWindow> data() {
    return data;
  }

  @Override
  public Iterator<SeriesWindow> iterator() {
    return data.iterator();
  }

  @Override
  public ValueType type() {
    return ValueType.RANGE_VECTOR;
  }
}

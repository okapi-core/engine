/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.promql;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;

/** One series in a MATRIX result. */
@Data
@AllArgsConstructor
public class MatrixSeries {
  @SerializedName("metric")
  private Map<String, String> metric;

  @SerializedName("values")
  private List<Sample> values;
}

/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.promql;

import com.google.gson.annotations.SerializedName;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One series in a VECTOR result. */
@NoArgsConstructor
@AllArgsConstructor
@Data
public class VectorSeries {
  @SerializedName("metric")
  private Map<String, String> metric;

  @SerializedName("value")
  private Sample value;
}

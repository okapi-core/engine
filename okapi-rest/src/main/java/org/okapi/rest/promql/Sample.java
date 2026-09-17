/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.promql;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Represents a Prometheus sample [timestamp, "value"]. Use a JsonDeserializer to map the JSON array
 * to this class.
 */
@Data
@AllArgsConstructor
public class Sample {
  @SerializedName(value = "0")
  private double timestamp;

  @SerializedName(value = "1")
  private String value;
}

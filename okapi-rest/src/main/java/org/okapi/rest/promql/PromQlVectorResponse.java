/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.promql;

import com.google.gson.annotations.SerializedName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Prometheus response for an instant vector query. */
@Data
@EqualsAndHashCode(callSuper = true)
public class PromQlVectorResponse extends GetPromQlResponse {
  @SerializedName("data")
  private PromQlVectorData data;
}

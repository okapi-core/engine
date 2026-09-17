/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.promql;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

/** Base type for Prometheus query data. The resultType field is the discriminator. */
@Data
public abstract class PromQlResultData {
  @SerializedName("resultType")
  private PromQlResultType resultType;
}

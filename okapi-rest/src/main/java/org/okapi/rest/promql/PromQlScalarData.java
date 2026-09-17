/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.promql;

import com.google.gson.annotations.SerializedName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Data payload for a Prometheus scalar query result. */
@Data
@EqualsAndHashCode(callSuper = true)
public class PromQlScalarData extends PromQlResultData {
  @SerializedName("result")
  private Sample result;

  public PromQlScalarData() {
    setResultType(PromQlResultType.SCALAR);
  }
}

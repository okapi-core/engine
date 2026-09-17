/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.promql;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Data payload for a Prometheus instant vector query result. */
@Data
@EqualsAndHashCode(callSuper = true)
public class PromQlVectorData extends PromQlResultData {
  @SerializedName("result")
  private List<VectorSeries> result;

  public PromQlVectorData() {
    setResultType(PromQlResultType.VECTOR);
  }
}

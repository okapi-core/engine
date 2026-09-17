/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.promql;

import com.google.gson.annotations.SerializedName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Data payload for a Prometheus string query result. */
@Data
@EqualsAndHashCode(callSuper = true)
public class PromQlStringData extends PromQlResultData {
  @SerializedName("result")
  private Sample result;

  public PromQlStringData() {
    setResultType(PromQlResultType.STRING);
  }
}

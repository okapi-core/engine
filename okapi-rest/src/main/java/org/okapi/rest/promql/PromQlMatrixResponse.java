/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.promql;

import com.google.gson.annotations.SerializedName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Prometheus response for a matrix query. */
@Data
@EqualsAndHashCode(callSuper = true)
public class PromQlMatrixResponse extends GetPromQlResponse {
  @SerializedName("data")
  private PromQlMatrixData data;
}

/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.promql;

import com.google.gson.annotations.SerializedName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Prometheus response for a string query. */
@Data
@EqualsAndHashCode(callSuper = true)
public class PromQlStringResponse extends GetPromQlResponse {
  @SerializedName("data")
  private PromQlStringData data;
}

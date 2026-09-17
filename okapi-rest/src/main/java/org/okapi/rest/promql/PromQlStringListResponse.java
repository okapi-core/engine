/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.promql;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Prometheus response for endpoints whose data payload is a string array. */
@Data
@EqualsAndHashCode(callSuper = true)
public class PromQlStringListResponse extends GetPromQlResponse {
  @SerializedName("data")
  private List<String> data;
}

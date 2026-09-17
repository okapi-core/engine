/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.promql;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Prometheus response for metric metadata. */
@Data
@EqualsAndHashCode(callSuper = true)
public class PromQlMetadataResponse extends GetPromQlResponse {
  @SerializedName("data")
  private Map<String, List<PromQlMetadataItem>> data;
}

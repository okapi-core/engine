/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.metrics.admin;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@AllArgsConstructor
@Getter
@Builder
public class NodeMetadataResponse {
  String ip;
  String id;

  @SerializedName("leader")
  boolean leader;
}

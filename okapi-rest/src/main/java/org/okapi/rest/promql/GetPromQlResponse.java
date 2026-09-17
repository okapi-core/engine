/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.promql;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

/** Top-level Prometheus HTTP API response envelope. Works for both success and error responses. */
@Data
public class GetPromQlResponse {
  @SerializedName("status")
  private String status; // "success" or "error"

  @SerializedName("errorType")
  private String errorType;

  @SerializedName("error")
  private String error;

  public boolean isSuccess() {
    return "success".equalsIgnoreCase(status);
  }
}

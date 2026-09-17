/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.promql;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** Enumerates Prometheus "resultType" values. */
@AllArgsConstructor
@Getter
public enum PromQlResultType {
  @SerializedName("vector")
  VECTOR("vector"),
  @SerializedName("matrix")
  MATRIX("matrix"),
  @SerializedName("scalar")
  SCALAR("scalar"),
  @SerializedName("string")
  STRING("string");

  private final String jsonValue;
}

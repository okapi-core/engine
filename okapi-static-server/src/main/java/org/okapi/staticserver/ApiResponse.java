/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.staticserver;

import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ApiResponse<T> {
  @Getter int code;
  T data;
  String error;

  public boolean isSuccess() {
    return code >= 200 && code < 300;
  }

  public boolean isError() {
    return !isSuccess();
  }

  public Optional<T> getData() {
    return Optional.ofNullable(data);
  }

  public Optional<String> getError() {
    return Optional.ofNullable(error);
  }
}

/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.demo.rest;

import jakarta.validation.constraints.NotBlank;

public class PingRequest {
  @NotBlank private String message;

  public PingRequest() {}

  public PingRequest(String message) {
    this.message = message;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }
}

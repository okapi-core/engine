/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.demo.rest;

public class PongResponse {
  private String message;
  private long timestamp;

  public PongResponse() {}

  public PongResponse(String message, long timestamp) {
    this.message = message;
    this.timestamp = timestamp;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public long getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(long timestamp) {
    this.timestamp = timestamp;
  }
}

/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.demo.rest;

public class ErrorResponse {
  private int status;
  private String error;
  private String path;

  public ErrorResponse() {}

  public ErrorResponse(int status, String error, String path) {
    this.status = status;
    this.error = error;
    this.path = path;
  }

  public int getStatus() {
    return status;
  }

  public void setStatus(int status) {
    this.status = status;
  }

  public String getError() {
    return error;
  }

  public void setError(String error) {
    this.error = error;
  }

  public String getPath() {
    return path;
  }

  public void setPath(String path) {
    this.path = path;
  }
}

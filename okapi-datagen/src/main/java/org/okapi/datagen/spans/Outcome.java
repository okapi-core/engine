/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.datagen.spans;

public enum Outcome {
  SUCCESS("success", 200),
  TIMEOUT("timeout", 504),
  APP_ERROR("app_error", 500),
  DEPENDENCY_ERROR("dependency_error", 502),
  CRASH("crash", 500);

  final String errorType;
  final int httpStatusCode;

  Outcome(String errorType, int httpStatusCode) {
    this.errorType = errorType;
    this.httpStatusCode = httpStatusCode;
  }

  static Outcome fromErrorType(ErrorType errorType) {
    return switch (errorType) {
      case TIMEOUT -> TIMEOUT;
      case APP_ERROR -> APP_ERROR;
      case DEPENDENCY_ERROR -> DEPENDENCY_ERROR;
      case CRASH -> CRASH;
    };
  }
}

/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.staticserver;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class Endpoints {
  // maybe straightfoward proxy would do ?
  @Value("${apiEndpoint}")
  private String apiEndpoint;

  public String getApiEndpoint() {
    return apiEndpoint;
  }

  public String getSignupEndpoint() {
    return apiEndpoint + "/api/v1/auth/sign-up";
  }

  public String getSigninEndpoint() {
    return apiEndpoint + "/api/v1/auth/sign-in";
  }

  public String getTempTokenEndpoint() {
    return apiEndpoint + "/api/v1/auth/temp-token";
  }

  public String getPingEndpoint() {
    return apiEndpoint + "/api/v1/auth/ping";
  }
}

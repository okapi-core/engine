/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.pg;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "okapi.data.pg")
public class PostgresCfg {
  @NotBlank
  private String url =
      System.getenv()
          .getOrDefault(
              "OKAPI_WEB_DB_URL",
              "jdbc:postgresql://127.0.0.1:5432/okapi_oscar?currentSchema=okapi_web");

  @NotBlank
  private String username = System.getenv().getOrDefault("OKAPI_WEB_DB_USER", "okapi_web_user");

  @NotBlank
  private String password =
      System.getenv().getOrDefault("OKAPI_WEB_DB_PASSWORD", "okapi_web_password");

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }
}

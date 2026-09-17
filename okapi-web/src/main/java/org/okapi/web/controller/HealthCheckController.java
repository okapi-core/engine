/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthCheckController {

  @GetMapping(path = "/internal/healthcheck")
  public String healthCheck() {
    return "ok";
  }
}

/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.oscar.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

  @GetMapping("/health")
  public String health() {
    return "OK";
  }
}

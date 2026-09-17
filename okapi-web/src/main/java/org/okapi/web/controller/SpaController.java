/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaController {

  @GetMapping({"/ui", "/ui/**"})
  public String index() {
    return "forward:/index.html";
  }
}

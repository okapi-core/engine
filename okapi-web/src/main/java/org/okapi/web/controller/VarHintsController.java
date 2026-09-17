/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.controller;

import org.okapi.web.dtos.dashboards.vars.GetVarHintsRequest;
import org.okapi.web.dtos.dashboards.vars.VarHintsResponse;
import org.okapi.web.service.dashboards.hints.VariableHintsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class VarHintsController {
  @Autowired VariableHintsService variableHintsService;

  @PostMapping("/variable-hints")
  public VarHintsResponse getVarHints(@RequestBody @Validated GetVarHintsRequest request) {
    return variableHintsService.getVarHints(request);
  }
}

/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.controller;

import org.okapi.rest.traces.red.ListServicesRequest;
import org.okapi.rest.traces.red.ServiceListResponse;
import org.okapi.rest.traces.red.ServiceRedRequest;
import org.okapi.rest.traces.red.ServiceRedResponse;
import org.okapi.web.service.query.RedsQueryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class RedsController {

  @Autowired private RedsQueryService redQueryService;

  @PostMapping("/reds")
  public ServiceRedResponse getReds(@Validated @RequestBody ServiceRedRequest request) {
    return redQueryService.getServicesReds(request);
  }

  @PostMapping("/services")
  public ServiceListResponse getServices(@Validated @RequestBody ListServicesRequest request) {
    return redQueryService.listServices(request);
  }
}

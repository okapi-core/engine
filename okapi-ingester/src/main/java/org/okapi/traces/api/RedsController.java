/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.traces.api;

import lombok.RequiredArgsConstructor;
import org.okapi.engine.api.TracesEngine;
import org.okapi.rest.traces.red.ListServicesRequest;
import org.okapi.rest.traces.red.ServiceListResponse;
import org.okapi.rest.traces.red.ServiceRedRequest;
import org.okapi.rest.traces.red.ServiceRedResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class RedsController {
  private final TracesEngine tracesEngine;

  @PostMapping("/reds")
  public ServiceRedResponse getReds(@Validated @RequestBody ServiceRedRequest request)
      throws Exception {
    return tracesEngine.reds(request);
  }

  @PostMapping("/services")
  public ServiceListResponse getServices(@Validated @RequestBody ListServicesRequest request)
      throws Exception {
    return tracesEngine.services(request);
  }
}

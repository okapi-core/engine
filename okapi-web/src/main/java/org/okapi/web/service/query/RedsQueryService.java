/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.service.query;

import lombok.RequiredArgsConstructor;
import org.okapi.ingester.client.IngesterClient;
import org.okapi.rest.traces.red.ListServicesRequest;
import org.okapi.rest.traces.red.ServiceListResponse;
import org.okapi.rest.traces.red.ServiceRedRequest;
import org.okapi.rest.traces.red.ServiceRedResponse;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RedsQueryService {

  private final IngesterClient ingesterClient;

  public ServiceListResponse listServices(ListServicesRequest request) {
    return ingesterClient.getSvcList(request);
  }

  public ServiceRedResponse getServicesReds(ServiceRedRequest request) {
    return ingesterClient.getServiceReds(request);
  }
}

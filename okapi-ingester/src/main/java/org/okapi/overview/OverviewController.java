/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.overview;

import lombok.RequiredArgsConstructor;
import org.okapi.rest.overview.IngesterOverviewRequest;
import org.okapi.rest.overview.IngesterOverviewResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class OverviewController {
  private final IngesterOverview ingesterOverview;

  @PostMapping(
      path = "/api/v1/overview",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public IngesterOverviewResponse overview(@RequestBody IngesterOverviewRequest request) {
    return ingesterOverview.overview(request);
  }
}

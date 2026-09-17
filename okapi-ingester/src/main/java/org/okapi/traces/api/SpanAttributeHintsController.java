/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.traces.api;

import lombok.RequiredArgsConstructor;
import org.okapi.engine.api.TracesEngine;
import org.okapi.rest.traces.SpanAttributeHintsRequest;
import org.okapi.rest.traces.SpanAttributeHintsResponse;
import org.okapi.rest.traces.SpanAttributeValueHintsRequest;
import org.okapi.rest.traces.SpanAttributeValueHintsResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class SpanAttributeHintsController {
  private final TracesEngine tracesEngine;

  @PostMapping("/spans/attributes/hints")
  public SpanAttributeHintsResponse getAttributeHints(
      @RequestBody SpanAttributeHintsRequest request) throws Exception {
    return tracesEngine.attributeHints(request);
  }

  @PostMapping("/spans/attributes/values/hints")
  public SpanAttributeValueHintsResponse getAttributeValueHints(
      @RequestBody SpanAttributeValueHintsRequest request) throws Exception {
    return tracesEngine.attributeValueHints(request);
  }
}

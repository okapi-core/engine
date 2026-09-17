/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.traces;

import lombok.RequiredArgsConstructor;
import org.okapi.engine.api.TracesEngine;
import org.okapi.rest.traces.SpanQueryV2Request;
import org.okapi.rest.traces.SpansFlameGraphResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/spans")
public class SpansFlameGraphController {
  private final TracesEngine tracesEngine;

  @PostMapping("/flamegraph")
  public SpansFlameGraphResponse getFlameGraph(@RequestBody SpanQueryV2Request request)
      throws Exception {
    return tracesEngine.flameGraph(request);
  }
}

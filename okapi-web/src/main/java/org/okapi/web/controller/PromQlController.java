/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.controller;

import java.util.List;
import lombok.AllArgsConstructor;
import org.okapi.web.service.query.PromQlService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@AllArgsConstructor
public class PromQlController {

  PromQlService promQlService;

  @GetMapping(value = "/query", produces = "application/json")
  public String queryGet(
      @RequestParam("query") String query,
      @RequestParam(value = "time", required = false) String time,
      @RequestParam(value = "timeout", required = false) String timeout) {
    return promQlService.queryPromQlInstant(query, time, timeout);
  }

  @PostMapping(
      value = "/query",
      consumes = "application/x-www-form-urlencoded",
      produces = "application/json")
  public String queryPost(
      @RequestParam("query") String query,
      @RequestParam(value = "time", required = false) String time,
      @RequestParam(value = "timeout", required = false) String timeout) {
    return promQlService.queryPromQlInstantPost(query, time, timeout);
  }

  @GetMapping(value = "/query_range", produces = "application/json")
  public String queryRange(
      @RequestParam("query") String query,
      @RequestParam("start") String start,
      @RequestParam("end") String end,
      @RequestParam("step") String step,
      @RequestParam(value = "timeout", required = false) String timeout) {
    return promQlService.queryPromQlRange(query, start, end, step, timeout);
  }

  @PostMapping(
      value = "/query_range",
      consumes = "application/x-www-form-urlencoded",
      produces = "application/json")
  public String queryRangePost(
      @RequestParam("query") String query,
      @RequestParam("start") String start,
      @RequestParam("end") String end,
      @RequestParam("step") String step,
      @RequestParam(value = "timeout", required = false) String timeout) {
    return promQlService.queryPromQlRangePost(query, start, end, step, timeout);
  }

  @GetMapping(value = "/labels", produces = "application/json")
  public String listLabels(
      @RequestParam(value = "start", required = false) String start,
      @RequestParam(value = "end", required = false) String end,
      @RequestParam(value = "match[]", required = false) List<String> matchers) {
    return promQlService.queryPromQlLabels(start, end, matchers);
  }

  @GetMapping(value = "/label/{label}/values", produces = "application/json")
  public String listLabelValues(
      @PathVariable("label") String label,
      @RequestParam(value = "start", required = false) String start,
      @RequestParam(value = "end", required = false) String end,
      @RequestParam(value = "match[]", required = false) List<String> matchers) {
    return promQlService.queryPromQlLabelValues(label, start, end, matchers);
  }

  @GetMapping(value = "/metadata", produces = "application/json")
  public String metadata(
      @RequestParam(value = "metric", required = false) String metric,
      @RequestParam(value = "limit", required = false) Integer limit) {
    return promQlService.queryPromQlMetadata(metric, limit);
  }
}

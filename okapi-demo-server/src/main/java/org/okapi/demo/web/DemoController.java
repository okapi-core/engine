/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.demo.web;

import jakarta.validation.Valid;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.okapi.demo.rest.PingRequest;
import org.okapi.demo.rest.PongResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/")
@Validated
public class DemoController {
  private static final Logger log = LoggerFactory.getLogger(DemoController.class);

  @PostMapping("/pass")
  public ResponseEntity<PongResponse> pass(@Valid @RequestBody PingRequest ping) {
    var response = new PongResponse("pong", Instant.now().toEpochMilli());
    return ResponseEntity.ok(response);
  }

  @GetMapping("/fail")
  public ResponseEntity<Void> fail() {
    log.warn("Simulating illegal state failure");
    throw new IllegalStateException("Forced failure");
  }

  @GetMapping("/error")
  public ResponseEntity<Void> error() {
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Forced bad request");
  }

  @GetMapping("/maybe-fail")
  public ResponseEntity<PongResponse> maybeFail() {
    if (ThreadLocalRandom.current().nextBoolean()) {
      log.info("maybe-fail returning pong");
      return ResponseEntity.ok(new PongResponse("pong", Instant.now().toEpochMilli()));
    }
    log.warn("maybe-fail returning bad request");
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unlucky roll triggered failure");
  }

  @GetMapping("/health")
  public Map<String, String> health() {
    return Map.of("status", "ok");
  }
}

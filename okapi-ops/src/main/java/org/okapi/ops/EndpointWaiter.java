/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.ops;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class EndpointWaiter {
  private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
  private static final Duration DEFAULT_INTERVAL = Duration.ofSeconds(1);

  private EndpointWaiter() {}

  public static void waitForEndpoint(String endpoint) {
    waitForEndpoint(endpoint, DEFAULT_TIMEOUT, DEFAULT_INTERVAL);
  }

  public static void waitForEndpoint(String endpoint, Duration timeout, Duration interval) {
    var deadline = System.nanoTime() + timeout.toNanos();
    var client = HttpClient.newBuilder().connectTimeout(interval).build();
    var request =
        HttpRequest.newBuilder().uri(URI.create(endpoint)).timeout(interval).GET().build();
    while (System.nanoTime() < deadline) {
      try {
        client.send(request, HttpResponse.BodyHandlers.discarding());
        return;
      } catch (IOException | InterruptedException e) {
        if (e instanceof InterruptedException) {
          Thread.currentThread().interrupt();
          throw new IllegalStateException("Interrupted while waiting for endpoint " + endpoint, e);
        }
        try {
          Thread.sleep(interval.toMillis());
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          throw new IllegalStateException("Interrupted while waiting for endpoint " + endpoint, ie);
        }
      }
    }
    throw new IllegalStateException("Endpoint not available after " + timeout + ": " + endpoint);
  }
}

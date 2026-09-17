/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.staticserver;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

@Component
public class ProxyServer {
  @Autowired OkHttpClient httpClient;

  @Value("${apiEndpoint}")
  String endpoint;

  public ResponseEntity<byte[]> proxyRequest(String loginToken, HttpServletRequest request)
      throws IOException {
    // This method should implement the logic to proxy the request to the given URL
    // For now, we return a placeholder response
    var proxyRequest = new Request.Builder();
    if (request.getMethod().equals("GET")) {
      proxyRequest.get();
    } else if (request.getMethod().equals("POST")) {
      var body = StreamUtils.copyToByteArray(request.getInputStream());
      proxyRequest.post(RequestBody.create(body, okhttp3.MediaType.parse("application/json")));
    }

    var headers = new Headers.Builder();
    Collections.list(request.getHeaderNames())
        .forEach(
            name -> {
              if (name.equals("Host")) {
                return; // Skip Host header to avoid conflicts
              }
              Collections.list(request.getHeaders(name))
                  .forEach(
                      value -> {
                        headers.add(name, request.getHeader(name));
                      });
            });
    proxyRequest.headers(headers.build());
    proxyRequest.url(endpoint + request.getRequestURI());
    try (var response = httpClient.newCall(proxyRequest.build()).execute()) {
      if (response.code() < 500) {
        return ResponseEntity.status(response.code()).body(collectResponseBody(response.body()));
      } else {
        return ResponseEntity.status(response.code())
            .body("Something went wrong, please try again.".getBytes(StandardCharsets.UTF_8));
      }
    }
  }

  public byte[] collectResponseBody(ResponseBody body) throws IOException {
    if (body == null) {
      return new byte[0];
    }
    if (body.contentLength() == 0) {
      return new byte[0];
    }
    return body.bytes();
  }
}

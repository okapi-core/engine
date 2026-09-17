/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.staticserver.controller;

import jakarta.servlet.http.HttpServletResponse;
import java.io.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class StaticServerController {
  @Value("${cookies.secureOnly}")
  boolean secureOnlyCookies;

  @Value("${cookies.httpOnly}")
  boolean httpOnlyCookies;

  private static void sendFile(HttpServletResponse servletResponse, String filePath)
      throws IOException {
    try (var is = new FileInputStream(filePath)) {
      byte[] buffer = new byte[1024];
      int bytesRead;
      while ((bytesRead = is.read(buffer)) != -1) {
        servletResponse.getOutputStream().write(buffer, 0, bytesRead);
      }
    } catch (FileNotFoundException e) {
      servletResponse.sendError(HttpServletResponse.SC_NOT_FOUND, "File not found: " + filePath);
    }
  }

  @GetMapping("/static/app.js")
  public void app(HttpServletResponse servletResponse) throws IOException {
    servletResponse.setContentType("application/javascript");
    sendFile(servletResponse, "dist/app.js");
  }

  @GetMapping("/")
  public void index(HttpServletResponse servletResponse) throws IOException {
    servletResponse.setContentType("text/html");
    sendFile(servletResponse, "dist/index.html");
  }
}

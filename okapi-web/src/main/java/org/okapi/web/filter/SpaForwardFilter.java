/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Controller;
import org.springframework.web.filter.OncePerRequestFilter;

@Controller
public class SpaForwardFilter extends OncePerRequestFilter {

  private static final Set<String> NON_SPA_PREFIXES =
      Set.of("/api", "/internal", "/assets", "/ui", "/v3/api-docs");

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    var method = request.getMethod();
    var path = getPath(request);
    var isMethodHeadOrGet = Objects.equals("GET", method) || Objects.equals("HEAD", method);
    var isSpaPath = NON_SPA_PREFIXES.stream().noneMatch(path::startsWith);
    if (isMethodHeadOrGet && isSpaPath) {
      RequestDispatcher dispatcher = request.getRequestDispatcher("/ui");
      dispatcher.forward(request, response);
    } else {
      filterChain.doFilter(request, response);
    }
  }

  private static String getPath(HttpServletRequest request) {
    String path = request.getRequestURI();
    String contextPath = request.getContextPath();
    if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
      return path.substring(contextPath.length());
    }
    return path;
  }
}

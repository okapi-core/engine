/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.spring.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Objects;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  DaoAuthenticationProvider daoAuthenticationProvider(
      UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
    var provider = new DaoAuthenticationProvider(userDetailsService);
    provider.setPasswordEncoder(passwordEncoder);
    return provider;
  }

  @Bean
  AuthenticationManager authenticationManager(AuthenticationConfiguration configuration)
      throws Exception {
    return configuration.getAuthenticationManager();
  }

  @Bean
  SecurityContextRepository securityContextRepository() {
    return new HttpSessionSecurityContextRepository();
  }

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http.csrf(csrf -> csrf.disable())
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(HttpMethod.POST, "/api/v1/users", "/api/v1/users/sign-in")
                    .permitAll()
                    .requestMatchers(SecurityConfiguration::isPublicFrontendRoute)
                    .permitAll()
                    .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml")
                    .permitAll()
                    .requestMatchers("/internal/healthcheck")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            exceptions ->
                exceptions
                    .authenticationEntryPoint(
                        (request, response, error) ->
                            response.sendError(HttpServletResponse.SC_UNAUTHORIZED))
                    .accessDeniedHandler(
                        (request, response, error) ->
                            response.sendError(HttpServletResponse.SC_FORBIDDEN)))
        .logout(
            logout ->
                logout
                    .logoutUrl("/api/v1/users/sign-out")
                    .invalidateHttpSession(true)
                    .deleteCookies("JSESSIONID")
                    .logoutSuccessHandler(
                        (request, response, authentication) ->
                            response.setStatus(HttpServletResponse.SC_NO_CONTENT)))
        .build();
  }

  private static boolean isPublicFrontendRoute(HttpServletRequest request) {
    var method = request.getMethod();
    var isMethodHeadOrGet =
        Objects.equals(HttpMethod.GET.name(), method)
            || Objects.equals(HttpMethod.HEAD.name(), method);
    if (!isMethodHeadOrGet) {
      return false;
    }
    var path = getPath(request);
    return Objects.equals("/", path)
        || Objects.equals("/index.html", path)
        || Objects.equals("/ui", path)
        || path.startsWith("/ui/")
        || path.startsWith("/assets/")
        || path.startsWith("/main/");
  }

  private static String getPath(HttpServletRequest request) {
    var path = request.getRequestURI();
    var contextPath = request.getContextPath();
    if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
      return path.substring(contextPath.length());
    }
    return path;
  }
}

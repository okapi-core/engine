/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.traces;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.*;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HttpFilters {
  @JsonPropertyDescription("HTTP request method to filter by (e.g. GET, POST).")
  String httpMethod;

  @JsonPropertyDescription(
      "HTTP response status code to filter by (e.g. 200, 500). Omit this field entirely if you do not want to filter by status code.")
  Integer statusCode;

  @JsonPropertyDescription("HTTP request origin header value to filter by.")
  String origin;

  @JsonPropertyDescription(
      "HTTP target host to filter by. Maps to the http.host or server.address span attribute.")
  String host;
}

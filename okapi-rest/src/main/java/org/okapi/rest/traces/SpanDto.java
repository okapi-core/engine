/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.traces;

import java.util.HashMap;
import java.util.Map;
import lombok.*;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class SpanDto {
  private String svc;
  private byte[] traceId;
  private byte[] spanId;
  private byte[] parentSpanId; // nullable
  private String name;
  private String kind; // SpanKind name
  private String statusCode; // Status code name
  private long startTimeMillis;
  private long endTimeMillis;
  private Map<String, String> attributes = new HashMap<>(); // flattened string attributes
}

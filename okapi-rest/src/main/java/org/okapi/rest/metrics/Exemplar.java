/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.metrics;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.okapi.rest.common.KeyValueJson;
import org.okapi.rest.common.NumberValue;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
public class Exemplar {
  String metric;
  Map<String, String> tags;
  long tsNanos;
  List<KeyValueJson> kv;
  NumberValue measurement;
  String spanId;
  String traceId;
}

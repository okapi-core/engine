/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.metrics.ch.template;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.okapi.ch.AbstractChTemplate;

@AllArgsConstructor
@Getter
@Builder
public class ChGetExemplarTemplate extends AbstractChTemplate {
  String fqTable;
  String metricName;
  Map<String, String> tags;
  long tsNanosStart;
  long tsNanosEnd;
}

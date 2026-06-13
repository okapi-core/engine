/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.metrics.ch.template;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@AllArgsConstructor
@Getter
@Builder
public class ChGetSumQueryTemplate {
  String table;
  String metric;
  Map<String, String> tags;
  String unit;
  String sumsType;
  long ts;
  long te;
}

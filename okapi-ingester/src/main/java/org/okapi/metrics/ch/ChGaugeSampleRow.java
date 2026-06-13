/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.metrics.ch;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.okapi.ch.AbstractChRow;

import java.util.Map;

@AllArgsConstructor
@Getter
@Builder
public class ChGaugeSampleRow extends AbstractChRow {
  long timestamp;
  String metric;
  Map<String, String> tags;
  float value;
  String unit;
}

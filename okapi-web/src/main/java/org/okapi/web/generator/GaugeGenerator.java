/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.generator;

import java.util.List;

public interface GaugeGenerator {
  public record GaugeSample(List<Long> ts, List<Float> values) {}

  GaugeSample generate(long start, int nSamples);
}

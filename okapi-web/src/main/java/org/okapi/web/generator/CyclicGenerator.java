/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.generator;

import java.util.ArrayList;
import java.util.Random;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class CyclicGenerator implements GaugeGenerator {
  long cycles;

  // Jitter config
  double jitterMean; // mean of the noise
  double jitterStdDev; // stddev of the noise

  private static final Random RNG = new Random();

  @Override
  public GaugeSample generate(long st, int nSamples) {
    var ts = new ArrayList<Long>(Math.max(0, nSamples));
    var vals = new ArrayList<Float>(Math.max(0, nSamples));

    if (nSamples <= 0) {
      return new GaugeSample(ts, vals);
    }

    final long stepMs = 1_000L; // 1 second between samples

    // Random phase in [0, 2π) per generate() call
    final double randomPhase = RNG.nextDouble() * 2.0 * Math.PI;

    for (int i = 0; i < nSamples; i++) {
      ts.add(st + i * stepMs);

      // Use nSamples - 1 so we span full [0, 2π * cycles]
      double progress = (nSamples > 1) ? (double) i / (double) (nSamples - 1) : 0.0;
      double angle = randomPhase + progress * 2.0 * Math.PI * (double) cycles;

      // Base cosine in [0, 1]
      double baseValue = (Math.cos(angle) + 1.0) / 2.0;

      // Gaussian jitter: mean + stddev * N(0,1)
      double jitter = jitterMean + jitterStdDev * RNG.nextGaussian();

      float value = (float) (baseValue + jitter);

      // Optional: clamp if you want to stay in [0,1]
      // value = Math.max(0f, Math.min(1f, value));

      vals.add(value);
    }

    return new GaugeSample(ts, vals);
  }
}

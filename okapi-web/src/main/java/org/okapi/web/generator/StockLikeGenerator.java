/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.generator;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class StockLikeGenerator implements GaugeGenerator {

  CyclicGenerator cyclicReturns =
      new CyclicGenerator(
          1L, // cycles (very slow regime change)
          0.0, // jitterMean
          0.5 // jitterStdDev – high because we later scale it down
          );

  // Price config
  final float startPrice; // e.g. 100.0
  final double driftPerStep; // e.g. 0.0002 ≈ 0.02% upward drift per sample

  @Override
  public GaugeSample generate(long st, int nSamples) {
    // 1. Use CyclicGenerator to produce "returns" in some small range
    GaugeSample raw = cyclicReturns.generate(st, nSamples);
    List<Long> ts = raw.ts();
    List<Float> returns = raw.values();

    var prices = new ArrayList<Float>(returns.size());

    if (returns.isEmpty()) {
      return new GaugeSample(ts, prices);
    }

    double price = startPrice;

    for (int i = 0; i < returns.size(); i++) {
      // Interpret raw value as a small return around 0.
      // Assume base generator outputs roughly in [-1, 1].
      // Scale it down to something like [-2%, +2%].
      double r = returns.get(i); // from cyclic+jitter
      double scaledReturn = r * 0.02; // 2% max move per step

      // Add drift
      scaledReturn += driftPerStep;

      // Geometric-ish update: price_{t+1} = price_t * (1 + scaledReturn)
      price = price * (1.0 + scaledReturn);

      // Ensure non-negative
      if (price < 0.01) {
        price = 0.01;
      }

      prices.add((float) price);
    }

    return new GaugeSample(ts, prices);
  }
}

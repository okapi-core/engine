/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.generator;

import java.util.ArrayList;
import java.util.Random;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class InternetTrafficGenerator implements GaugeGenerator {

  // internal preset params
  private final float baseline;
  private final float dailyAmplitude;
  private final float weeklyAmplitude;
  private final double jitterStdDev;
  private final long stepMs;

  private static final Random RNG = new Random();

  /**
   * Returns a generator tuned for second-resolution traffic with strong hourly + daily variations,
   * usable for graphs spanning hours.
   */
  public static InternetTrafficGenerator defaultHourly() {
    return new InternetTrafficGenerator(
        2500.0f, // baseline QPS (~medium service)
        0.55f, // dailyAmplitude ±55%
        0.15f, // weeklyAmplitude ±15% weekday/weekend shift
        0.04, // jitter 4% noise
        1000L // 1 second resolution
        );
  }

  /**
   * Returns a generator tuned for minute-resolution traffic with weekly + daily cycles, usable for
   * graphs spanning weeks.
   */
  public static InternetTrafficGenerator defaultWeekly() {
    return new InternetTrafficGenerator(
        1800.0f, // baseline QPS (~average service)
        0.65f, // dailyAmplitude ±65%
        0.25f, // weeklyAmplitude ±25%
        0.05, // jitter 5% noise
        60_000L // 1 minute resolution
        );
  }

  @Override
  public GaugeSample generate(long st, int nSamples) {
    var ts = new ArrayList<Long>(Math.max(0, nSamples));
    var vals = new ArrayList<Float>(Math.max(0, nSamples));

    if (nSamples <= 0) {
      return new GaugeSample(ts, vals);
    }

    // Pick a random phase once so curves don't always align
    final double randomPhase = RNG.nextDouble() * 2.0 * Math.PI;

    final double secPerDay = 24 * 60 * 60;
    final double secPerWeek = 7 * secPerDay;

    final long startSec = st / 1000L;
    final long stepSec = stepMs / 1000L;

    for (int i = 0; i < nSamples; i++) {
      long tsMs = st + i * stepMs;
      long tSec = startSec + i * stepSec;

      ts.add(tsMs);

      double dayProgress = (tSec % (long) secPerDay) / secPerDay;
      double weekProgress = (tSec % (long) secPerWeek) / secPerWeek;

      // Hourly pattern baked into per-point value
      double hourlyProgress = (tSec % 3600) / 3600.0;
      double hourlyCycle = Math.cos(2.0 * Math.PI * (hourlyProgress - 0.3));

      double dailyCycle = Math.cos(2.0 * Math.PI * (dayProgress - 0.25));
      double weeklyCycle = Math.cos(2.0 * Math.PI * (weekProgress - 0.1));

      double value =
          baseline
                  * (1.0 + dailyAmplitude * dailyCycle)
                  * (1.0 + weeklyAmplitude * weeklyCycle)
                  * (1.0 + 0.3 * hourlyCycle) // ~30% hourly swing baked in
              + Math.sin(randomPhase + i * 0.0005) * baseline * 0.02; // tiny organic wobble

      // Gaussian noise jitter
      value += jitterStdDev * baseline * RNG.nextGaussian();

      if (value < 0.0) {
        value = 0.0;
      }

      vals.add((float) value);
    }

    return new GaugeSample(ts, vals);
  }
}
